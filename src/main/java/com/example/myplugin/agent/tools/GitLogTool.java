package com.example.myplugin.agent.tools;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentTool;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GitLogTool implements AgentTool {

    private static final int MAX_OUTPUT_CHARS = 20000;

    private final AgentContext context;

    public GitLogTool(AgentContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "git_log";
    }

    @Override
    public String description() {
        return "Show the recent commit history (git log --oneline). "
                + "Optionally limit to a specific file path and control the number of commits.";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description(description())
                .parameters(JsonObjectSchema.builder()
                        .addIntegerProperty("count", "Number of commits to show (default 20, max 100)")
                        .addStringProperty("path", "Only show commits touching this file, relative to project root (optional)")
                        .build())
                .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        int count = 20;
        if (arguments.has("count") && !arguments.get("count").isJsonNull()) {
            try {
                count = arguments.get("count").getAsInt();
            } catch (Exception ignored) {
            }
        }
        count = Math.max(1, Math.min(100, count));

        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("log");
        command.add("--oneline");
        command.add("-" + count);
        if (arguments.has("path") && !arguments.get("path").isJsonNull()) {
            String path = arguments.get("path").getAsString().trim();
            if (!path.isEmpty()) {
                command.add("--");
                command.add(path.replace('\\', '/'));
            }
        }

        CommandResult result = run(command, 60);

        if (result.exitCode != 0) {
            StringBuilder error = new StringBuilder();
            error.append("Error running git log (exit code ").append(result.exitCode).append(")");
            if (!result.stderr.isBlank()) {
                error.append(":\n").append(result.stderr.trim());
            }
            return error.toString();
        }

        if (result.stdout.isBlank()) {
            return "No commits found.";
        }
        if (result.stdout.length() > MAX_OUTPUT_CHARS) {
            return result.stdout.substring(0, MAX_OUTPUT_CHARS) + "\n... (truncated)";
        }
        return result.stdout;
    }

    private CommandResult run(List<String> command, int timeoutSeconds) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(context.getBaseDir().toFile());

            Process process = pb.start();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            Thread stdoutThread = capture(process.getInputStream(), stdout);
            Thread stderrThread = capture(process.getErrorStream(), stderr);
            stdoutThread.start();
            stderrThread.start();

            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new CommandResult(-1, "", "Error: timed out after " + timeoutSeconds + " seconds");
            }
            stdoutThread.join(2000);
            stderrThread.join(2000);
            return new CommandResult(process.exitValue(), stdout.toString(), stderr.toString());
        } catch (Exception e) {
            return new CommandResult(-1, "", "Error: " + e.getMessage());
        }
    }

    private Thread capture(java.io.InputStream stream, StringBuilder target) {
        return new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (target) {
                        if (target.length() > MAX_OUTPUT_CHARS + 10000) break;
                        target.append(line).append('\n');
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    private static class CommandResult {
        final int exitCode;
        final String stdout;
        final String stderr;

        CommandResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }
}
