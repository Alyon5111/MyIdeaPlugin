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
import java.util.Comparator;
import java.util.List;

public class ListFilesTool implements AgentTool {

    /** One directory listing must stay small enough to be read once and act on. */
    static final int DEFAULT_MAX_ENTRIES = 40;

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
                + "Lists ONE level by default; pass recursive=true to descend into subdirectories. "
                + "Path is relative to the project root. Use empty path \"\" to list the project root.";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description(description())
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("path", "Directory path relative to project root (empty for root)")
                        .addBooleanProperty("recursive", "Descend into subdirectories (default: false, one level only)")
                        .build())
                .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        String relPath = arguments.has("path") && !arguments.get("path").isJsonNull()
                ? arguments.get("path").getAsString() : "";
        boolean recursive = arguments.has("recursive") && !arguments.get("recursive").isJsonNull()
                && arguments.get("recursive").getAsBoolean();

        Path baseDir = context.getBaseDir();
        Path dir = relPath.isEmpty() ? baseDir : baseDir.resolve(relPath);
        if (!dir.normalize().startsWith(baseDir)) {
            return "Error: path escapes project directory";
        }

        return list(baseDir, relPath, recursive, DEFAULT_MAX_ENTRIES);
    }

    /**
     * Renders a directory listing. Extracted from {@link #execute} so the contract
     * (one level by default, bounded size) can be verified without an IDE project.
     */
    static String list(Path root, String relPath, boolean recursive, int maxEntries) {
        Path dir = (relPath == null || relPath.isEmpty()) ? root : root.resolve(relPath);

        if (!Files.exists(dir)) {
            return "Error: path not found: " + relPath;
        }
        if (!Files.isDirectory(dir)) {
            return "Error: not a directory: " + relPath;
        }

        StringBuilder sb = new StringBuilder();
        Counter counter = new Counter();
        listDir(dir, sb, 0, recursive, maxEntries, counter);
        if (counter.skipped > 0) {
            sb.append("... (").append(counter.skipped).append(" more entries not shown; ")
                    .append("narrow the path, or pass recursive=true)\n");
        }
        return sb.toString();
    }

    private static void listDir(Path dir, StringBuilder sb, int depth, boolean recursive,
                                int maxEntries, Counter counter) {
        List<Path> entries;
        try (var stream = Files.list(dir)) {
            entries = stream.sorted(Comparator
                    .comparing((Path p) -> !Files.isDirectory(p))
                    .thenComparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (IOException e) {
            sb.append("Error reading directory: ").append(e.getMessage()).append("\n");
            return;
        }

        for (Path entry : entries) {
            String name = entry.getFileName().toString();
            if (excludedName(name)) {
                continue;
            }
            if (counter.emitted >= maxEntries) {
                counter.skipped++;
                continue;
            }
            counter.emitted++;

            String indent = "  ".repeat(depth);
            if (Files.isDirectory(entry)) {
                sb.append(indent).append(name).append("/\n");
                if (recursive) {
                    listDir(entry, sb, depth + 1, true, maxEntries, counter);
                }
            } else {
                sb.append(indent).append(name);
                long size = sizeOf(entry);
                if (size > 0) {
                    sb.append(" (").append(formatSize(size)).append(")");
                }
                sb.append("\n");
            }
        }
    }

    static boolean excludedName(String name) {
        return name.startsWith(".") || name.equals("node_modules")
                || name.equals("build") || name.equals("target")
                || name.equals("__pycache__") || name.equals(".git");
    }

    private static long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0;
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return (bytes / (1024 * 1024)) + " MB";
    }

    private static class Counter {
        int emitted;
        int skipped;
    }
}
