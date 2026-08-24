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

public class RunTestsTool implements AgentTool {

    private final AgentContext context;

    public RunTestsTool(AgentContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "run_tests";
    }

    @Override
    public String description() {
        return "Run the project tests via gradle. Optionally run only a specific test class "
                + "(pass the fully qualified or simple class name). Returns the test results summary.";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description(description())
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("test_class", "Test class name to run (optional, runs all tests if omitted)")
                        .build())
                .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        List<String> args = new ArrayList<>();
        args.add("test");
        args.add("--console=plain");

        if (arguments.has("test_class") && !arguments.get("test_class").isJsonNull()) {
            String testClass = arguments.get("test_class").getAsString().trim();
            if (!testClass.isEmpty()) {
                args.add("--tests");
                args.add(testClass);
            }
        }

        CommandResult result = runGradle(args, 600);

        StringBuilder sb = new StringBuilder();
        if (result.exitCode == 0) {
            sb.append("TESTS PASSED\n");
        } else {
            sb.append("TEST RUN FINISHED WITH EXIT CODE ").append(result.exitCode).append("\n");
        }

        String summary = extractSummary(result.output);
        if (!summary.isEmpty()) {
            sb.append("\n").append(summary).append("\n");
        }

        String tail = tail(result.output, 12000);
        if (!tail.isBlank()) {
            sb.append("\n--- Output (tail) ---\n").append(tail);
        }
        return sb.toString();
    }

    private String extractSummary(String output) {
        StringBuilder summary = new StringBuilder();
        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("> Task :test")
                    || trimmed.contains("FAILED")
                    || trimmed.contains("BUILD SUCCESSFUL")
                    || trimmed.contains("BUILD FAILED")
                    || trimmed.matches("\\d+ tests completed.*")
                    || trimmed.toLowerCase().startsWith("> test")) {
                summary.append(trimmed).append('\n');
            }
        }
        return summary.toString().trim();
    }

    private String tail(String s, int maxChars) {
        if (s.length() <= maxChars) return s;
        return "... (earlier output truncated)\n" + s.substring(s.length() - maxChars);
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
