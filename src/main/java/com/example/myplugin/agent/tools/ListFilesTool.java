package com.example.myplugin.agent.tools;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentTool;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ListFilesTool implements AgentTool {

    private final AgentContext context;

    public ListFilesTool(AgentContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "list_files";
    }

    @Override
    public String description() {
        return "List files and directories in a given path. "
                + "Returns a tree-like listing. Path is relative to the project root. "
                + "Use empty path \"\" to list the project root.";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description(description())
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("path", "Directory path relative to project root (empty for root)")
                        .build())
                .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        String relPath = arguments.has("path") && !arguments.get("path").isJsonNull()
                ? arguments.get("path").getAsString() : "";
        Path dir = relPath.isEmpty() ? context.getBaseDir() : context.getBaseDir().resolve(relPath);

        if (!dir.startsWith(context.getBaseDir())) {
            return "Error: path escapes project directory";
        }

        if (!Files.exists(dir)) {
            return "Error: path not found: " + relPath;
        }

        if (!Files.isDirectory(dir)) {
            return "Error: not a directory: " + relPath;
        }

        StringBuilder sb = new StringBuilder();
        try {
            listDir(dir, context.getBaseDir(), sb, 0, 100);
        } catch (TooManyEntriesException e) {
            sb.append("... (truncated, too many entries)");
        }

        return sb.toString();
    }

    private void listDir(Path dir, Path root, StringBuilder sb, int depth, int maxEntries)
            throws TooManyEntriesException {
        if (sb.length() > 10000) {
            throw new TooManyEntriesException();
        }

        try (var stream = Files.list(dir)) {
            var entries = stream.sorted((a, b) -> {
                boolean aDir = Files.isDirectory(a);
                boolean bDir = Files.isDirectory(b);
                if (aDir != bDir) return aDir ? -1 : 1;
                return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
            }).toList();

            for (Path entry : entries) {
                if (sb.length() > 10000) {
                    throw new TooManyEntriesException();
                }

                String name = entry.getFileName().toString();
                if (name.startsWith(".") || name.equals("node_modules")
                        || name.equals("build") || name.equals("target")
                        || name.equals("__pycache__") || name.equals(".git")) {
                    continue;
                }

                String indent = "  ".repeat(depth);
                if (Files.isDirectory(entry)) {
                    sb.append(indent).append(name).append("/\n");
                    if (depth < 5) {
                        listDir(entry, root, sb, depth + 1, maxEntries);
                    }
                } else {
                    long size = Files.exists(entry) ? 0 : 0;
                    try {
                        size = Files.size(entry);
                    } catch (IOException ignored) {
                    }
                    sb.append(indent).append(name);
                    if (size > 0) {
                        sb.append(" (").append(formatSize(size)).append(")");
                    }
                    sb.append("\n");
                }
            }
        } catch (IOException e) {
            sb.append("Error reading directory: ").append(e.getMessage()).append("\n");
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return (bytes / (1024 * 1024)) + " MB";
    }

    private static class TooManyEntriesException extends Exception {
    }
}
