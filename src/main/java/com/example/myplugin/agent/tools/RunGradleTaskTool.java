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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RunGradleTaskTool implements AgentTool {

    private static final int TIMEOUT_SECONDS = 600;
    private static final int MAX_TAIL_CHARS = 15000;
    private static final Pattern ARG_TOKENIZER = Pattern.compile("\"([^\"]*)\"|(\\S+)");

    private final AgentContext context;

    public RunGradleTaskTool(AgentContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "run_gradle_task";
    }

    @Override
    public String description() {
        return "Run an arbitrary gradle task in the project using its wrapper (gradlew), "
                + "falling back to a global gradle installation. Examples: build, clean, assemble, "
                + "check. For running tests prefer the run_tests tool.";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description(description())
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("task", "Gradle task name to run, e.g. \"build\"")
                        .addStringProperty("arguments", "Additional gradle arguments separated by spaces, e.g. \"--info --refresh-dependencies\" (optional)")
                        .required("task")
                        .build())
                .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        if (!arguments.has("task") || arguments.get("task").isJsonNull()) {
            return "Error: missing required parameter 'task'";
        }
        String task = arguments.get("task").getAsString().trim();
        if (task.isEmpty()) {
            return "Error: task must not be empty";
        }

        List<String> args = new ArrayList<>();
        args.add(task);
        args.add("--console=plain");
        if (arguments.has("arguments") && !arguments.get("arguments").isJsonNull()) {
            args.addAll(tokenize(arguments.get("arguments").getAsString()));
        }

        CommandResult result = runGradle(args, TIMEOUT_SECONDS);

        StringBuilder sb = new StringBuilder();
        sb.append("Task '").append(task).append("' finished with exit code ")
                .append(result.exitCode).append('\n');
        String tail = tail(result.output, MAX_TAIL_CHARS);
        if (!tail.isBlank()) {
            sb.append("\n--- Output (tail) ---\n").append(tail);
        }
        return sb.toString();
    }

    private List<String> tokenize(String s) {
        List<String> tokens = new ArrayList<>();
        Matcher m = ARG_TOKENIZER.matcher(s == null ? "" : s);
        while (m.find()) {
            tokens.add(m.group(1) != null ? m.group(1) : m.group(2));
        }
        return tokens;
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

    private String tail(String s, int maxChars) {
        if (s.length() <= maxChars) return s.trim();
        return "... (earlier output truncated)\n" + s.substring(s.length() - maxChars).trim();
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
