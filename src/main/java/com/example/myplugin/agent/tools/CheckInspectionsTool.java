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
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class CheckInspectionsTool implements AgentTool {

    private static final Set<String> VALID_SEVERITIES = Set.of("all", "error", "warning");
    private static final int MAX_ISSUES = 80;

    private final AgentContext context;

    public CheckInspectionsTool(AgentContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "check_inspections";
    }

    @Override
    public String description() {
        return "Check the whole project with the compiler and report all errors and warnings "
                + "(inspection-style feedback). Optionally filter by file path and severity "
                + "('error', 'warning', or 'all'). More general than get_diagnostics, which targets a single file.";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description(description())
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("path", "Only report issues for this file path relative to project root (optional)")
                        .addStringProperty("severity", "Filter severity: \"all\" (default), \"error\", or \"warning\"")
                        .build())
                .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        String severity = arguments.has("severity") && !arguments.get("severity").isJsonNull()
                ? arguments.get("severity").getAsString().trim().toLowerCase(Locale.ROOT) : "all";
        if (!VALID_SEVERITIES.contains(severity)) {
            return "Error: invalid severity '" + severity + "' (use all, error, or warning)";
        }

        Path filterFile = null;
        if (arguments.has("path") && !arguments.get("path").isJsonNull()) {
            String path = arguments.get("path").getAsString().trim();
            if (!path.isEmpty()) {
                filterFile = context.getBaseDir().resolve(path).normalize();
                if (!filterFile.startsWith(context.getBaseDir())) {
                    return "Error: path escapes project directory";
                }
                if (!Files.exists(filterFile)) {
                    return "Error: file not found: " + path;
                }
            }
        }

        CommandResult result = runGradle(List.of("compileJava", "--console=plain"), 300);

        List<String> issues = new ArrayList<>();
        int errors = 0;
        int warnings = 0;
        for (String line : result.output.split("\n")) {
            String trimmed = line.trim();
            boolean isError = isErrorLine(trimmed);
            boolean isWarning = isWarningLine(trimmed);
            if (!isError && !isWarning) continue;
            if (severity.equals("error") && !isError) continue;
            if (severity.equals("warning") && !isWarning) continue;
            if (filterFile != null) {
                String normalized = filterFile.toString().replace('\\', '/');
                String fileNameOnly = filterFile.getFileName().toString();
                if (!trimmed.contains(normalized) && !trimmed.contains(fileNameOnly)) continue;
            }
            if (isError) errors++;
            else warnings++;
            if (issues.size() < MAX_ISSUES) {
                issues.add(trimmed);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Compiler exit code: ").append(result.exitCode).append('\n');
        sb.append("Errors: ").append(errors).append(", warnings: ").append(warnings).append('\n');

        if (issues.isEmpty()) {
            if (result.exitCode == 0) {
                sb.append("\nNo issues found.");
            } else {
                sb.append("\nBuild failed but no matching compiler diagnostics were reported.");
            }
        } else {
            sb.append('\n');
            for (String issue : issues) {
                sb.append(issue).append('\n');
            }
            if (errors + warnings > MAX_ISSUES) {
                sb.append("... (showing first ").append(MAX_ISSUES)
                        .append(" of ").append(errors + warnings).append(" issues)\n");
            }
        }
        return sb.toString();
    }

    private boolean isErrorLine(String line) {
        return line.contains("error:") || line.matches("^e: .*");
    }

    private boolean isWarningLine(String line) {
        return line.contains("warning:") || line.matches("^w: .*");
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
                            if (output.length() > 80000) break;
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
            return new CommandResult(-1, "Error running gradle: " + e.getMessage());
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
