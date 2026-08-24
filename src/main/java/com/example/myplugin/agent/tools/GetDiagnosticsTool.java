package com.example.myplugin.agent.tools;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentTool;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GetDiagnosticsTool implements AgentTool {

    private final AgentContext context;

    public GetDiagnosticsTool(AgentContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "get_diagnostics";
    }

    @Override
    public String description() {
        return "Get compilation errors and warnings for a specific file by running the project build "
                + "(gradle compileJava). Returns matching error/warning lines with line numbers. "
                + "Useful after editing a file to verify it compiles.";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description(description())
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("path", "File path relative to project root")
                        .required("path")
                        .build())
                .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        if (!arguments.has("path") || arguments.get("path").isJsonNull()) {
            return "Error: missing required parameter 'path'";
        }
        String path = arguments.get("path").getAsString().trim();
        Path filePath = context.getBaseDir().resolve(path).normalize();

        if (!filePath.startsWith(context.getBaseDir())) {
            return "Error: path escapes project directory";
        }
        if (!Files.exists(filePath)) {
            return "Error: file not found: " + path;
        }

        CommandResult result = runGradle(List.of("compileJava", "--console=plain"), 300);

        List<String> issues = new ArrayList<>();
        String normalizedPath = path.replace('\\', '/');
        String fileNameOnly = filePath.getFileName().toString();
        for (String line : result.output.split("\n")) {
            String trimmed = line.trim();
            if (!isIssueLine(trimmed)) continue;
            if (trimmed.contains(normalizedPath) || trimmed.contains(fileNameOnly)) {
                issues.add(trimmed);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Compiler exit code: ").append(result.exitCode).append("\n");
        if (issues.isEmpty()) {
            if (result.exitCode == 0) {
                sb.append("No compilation issues reported for ").append(path);
            } else {
                sb.append("Build failed but no issues were attributed to ")
                        .append(path).append(". Other files may have errors.");
            }
        } else {
            sb.append("Issues for ").append(path).append(":\n");
            for (String issue : issues) {
                sb.append(issue).append("\n");
            }
            if (issues.size() > 50) {
                sb.append("\n... (truncated at 50 issues)");
            }
        }
        return sb.toString();
    }

    private boolean isIssueLine(String line) {
        return line.contains("error:") || line.contains("warning:")
                || line.matches("^e: .*") || line.matches("^w: .*");
    }

    private CommandResult runGradle(List<String> args, int timeoutSeconds) {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        Path wrapper = context.getBaseDir().resolve(windows ? "gradlew.bat" : "gradlew");

        List<String> command = new ArrayList<>();
        if (Files.exists(wrapper)) {
            if (windows) {
                command.add("cmd.exe");
                command.add("/c");
                command.add("gradlew.bat");
            } else {
                command.add("./gradlew");
            }
        } else {
            command.add("gradle");
        }
        command.addAll(args);
        return runCommand(command, timeoutSeconds);
    }

    private CommandResult runCommand(List<String> command, int timeoutSeconds) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(context.getBaseDir().toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            Thread reader = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        synchronized (output) {
                            if (output.length() > 60000) break;
                            output.append(line).append('\n');
                        }
                    }
                } catch (Exception ignored) {
                }
            });
            reader.start();

            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                reader.join(2000);
                return new CommandResult(-1, output + "\nError: timed out after " + timeoutSeconds + " seconds");
            }
            reader.join(2000);
            return new CommandResult(process.exitValue(), output.toString());
        } catch (Exception e) {
            return new CommandResult(-1, "Error running command: " + e.getMessage());
        }
    }

    private static class CommandResult {
        final int exitCode;
        final String output;

        CommandResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}
