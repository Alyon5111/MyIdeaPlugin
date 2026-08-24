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

public class GitDiffTool implements AgentTool {

    private static final int MAX_OUTPUT_CHARS = 30000;

    private final AgentContext context;

    public GitDiffTool(AgentContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "git_diff";
    }

    @Override
    public String description() {
        return "Show git diff of uncommitted changes in the project. "
                + "Optionally limit to a single file and/or show only staged changes.";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description(description())
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("path", "Limit the diff to this file path relative to project root (optional)")
                        .addBooleanProperty("staged", "Show only staged changes (default false)")
                        .build())
                .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        boolean staged = arguments.has("staged") && !arguments.get("staged").isJsonNull()
                && arguments.get("staged").getAsBoolean();

        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("diff");
        if (staged) {
            command.add("--staged");
        }
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
            error.append("Error running git diff (exit code ").append(result.exitCode).append(")");
            if (!result.stderr.isBlank()) {
                error.append(":\n").append(tail(result.stderr, 4000));
            }
            return error.toString();
        }

        if (result.stdout.isBlank()) {
            return staged ? "No staged changes." : "No unstaged changes.";
        }
        return tail(result.stdout, MAX_OUTPUT_CHARS);
    }

    private CommandResult run(List<String> command, int timeoutSeconds) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(context.getBaseDir().toFile());

            Process process = pb.start();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            Thread stdoutThread = capture(process.getInputStream(), stdout, MAX_OUTPUT_CHARS + 10000);
            Thread stderrThread = capture(process.getErrorStream(), stderr, 10000);
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

    private Thread capture(java.io.InputStream stream, StringBuilder target, int maxChars) {
        return new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (target) {
                        if (target.length() > maxChars) break;
                        target.append(line).append('\n');
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    private String tail(String s, int maxChars) {
        if (s.length() <= maxChars) return s;
        return "... (truncated)\n" + s.substring(s.length() - maxChars);
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
