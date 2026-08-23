package com.example.myplugin.agent.tools;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentTool;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class ExecuteCommandTool implements AgentTool {

    private final AgentContext context;

    public ExecuteCommandTool(AgentContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "execute_command";
    }

    @Override
    public String description() {
        return "Execute a shell command in the project directory. "
                + "Returns stdout and stderr. Use this for building, testing, "
                + "running scripts, git operations, etc.";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description(description())
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("command", "Shell command to execute")
                        .required("command")
                        .build())
                .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        String command = arguments.get("command").getAsString();

        try {
            ProcessBuilder pb = new ProcessBuilder();
            if (isWindows()) {
                pb.command("cmd.exe", "/c", command);
            } else {
                pb.command("sh", "-c", command);
            }
            pb.directory(context.getBaseDir().toFile());
            pb.redirectErrorStream(false);

            Process process = pb.start();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (stdout.length() > 30000) {
                            stdout.append("\n... (stdout truncated)");
                            break;
                        }
                        stdout.append(line).append("\n");
                    }
                } catch (Exception ignored) {
                }
            });

            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (stderr.length() > 10000) {
                            stderr.append("\n... (stderr truncated)");
                            break;
                        }
                        stderr.append(line).append("\n");
                    }
                } catch (Exception ignored) {
                }
            });

            stdoutThread.start();
            stderrThread.start();

            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            stdoutThread.join(2000);
            stderrThread.join(2000);

            if (!finished) {
                process.destroyForcibly();
                return "Error: command timed out after 120 seconds";
            }

            int exitCode = process.exitValue();
            StringBuilder result = new StringBuilder();
            result.append("Exit code: ").append(exitCode).append("\n");
            if (stdout.length() > 0) {
                result.append("\nstdout:\n").append(stdout);
            }
            if (stderr.length() > 0) {
                result.append("\nstderr:\n").append(stderr);
            }

            return result.toString();
        } catch (Exception e) {
            return "Error executing command: " + e.getMessage();
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
