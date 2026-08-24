package com.example.myplugin.agent.tools;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentTool;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.VfsUtil;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FindClassesTool implements AgentTool {

    private static final Logger LOG = Logger.getInstance(FindClassesTool.class);
    private static final Pattern CLASS_DEF = Pattern.compile(
            "(?:public\\s+)?(?:abstract\\s+)?(?:final\\s+)?(?:static\\s+)?(?:sealed\\s+)?(?:non-sealed\\s+)?" +
                    "(?:class|interface|enum|record)\\s+(\\w+)");
    private final AgentContext context;

    public FindClassesTool(AgentContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "find_classes";
    }

    @Override
    public String description() {
        return "Search for Java classes by name (partial or full). "
                + "Returns matching class names, their file locations, and package. "
                + "Searches file contents for class/interface/enum/record definitions.";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description(description())
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("name", "Class name or partial name to search for")
                        .required("name")
                        .build())
                .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        String name = arguments.get("name").getAsString().trim();
        Project project = context.getProject();

        if (DumbService.isDumb(project)) {
            return "Error: IDE is indexing, please wait and try again";
        }

        try {
            return ReadAction.compute(() -> doSearch(project, name));
        } catch (Exception e) {
            LOG.warn("FindClassesTool search failed", e);
            return "Error searching classes: " + e.getMessage();
        }
    }

    @NotNull
    private String doSearch(@NotNull Project project, @NotNull String name) {
        VirtualFile baseDir = LocalFileSystem.getInstance().findFileByPath(context.getBaseDir().toAbsolutePath().toString());
        if (baseDir == null) {
            return "Error: cannot access project directory";
        }

        List<String> results = new ArrayList<>();
        int maxResults = 50;

        VfsUtil.visitChildrenRecursively(baseDir, new VirtualFileVisitor<>() {
            @Override
            public boolean visitFile(@NotNull VirtualFile file) {
                if (results.size() >= maxResults) return false;
                if (isIgnored(file)) return true;
                if (!file.getName().endsWith(".java")) return true;

                try {
                    byte[] bytes = file.contentsToByteArray();
                    String content = new String(bytes, StandardCharsets.UTF_8);
                    String[] lines = content.split("\n");

                    String packageName = "";
                    for (String line : lines) {
                        if (line.startsWith("package ")) {
                            packageName = line.replace("package ", "").replace(";", "").trim();
                        }
                        Matcher m = CLASS_DEF.matcher(line);
                        if (m.find()) {
                            String className = m.group(1);
                            if (className.toLowerCase().contains(name.toLowerCase())) {
                                String relPath = VfsUtil.getRelativePath(file, baseDir, '/');
                                String modifiers = extractModifiers(line);
                                String classType = extractClassType(line);

                                results.add(String.format("%s [%s] %s  [package: %s]  [file: %s]",
                                        modifiers, classType, className,
                                        packageName.isEmpty() ? "(default)" : packageName,
                                        relPath != null ? relPath : file.getPath()));
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
                return true;
            }
        });

        if (results.isEmpty()) {
            return "No classes found matching: " + name;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(results.size()).append(" class(es):\n\n");
        for (String r : results) {
            sb.append(r).append("\n");
        }
        if (results.size() >= maxResults) {
            sb.append("\n... (truncated at ").append(maxResults).append(" results)");
        }
        return sb.toString();
    }

    @NotNull
    private String extractClassType(@NotNull String line) {
        String trimmed = line.trim().toLowerCase();
        if (trimmed.contains("interface")) return "interface";
        if (trimmed.contains("enum")) return "enum";
        if (trimmed.contains("record")) return "record";
        return "class";
    }

    @NotNull
    private String extractModifiers(@NotNull String line) {
        String trimmed = line.trim();
        List<String> mods = new ArrayList<>();
        if (trimmed.startsWith("public")) mods.add("public");
        else if (trimmed.startsWith("protected")) mods.add("protected");
        else if (trimmed.startsWith("private")) mods.add("private");
        if (trimmed.contains("abstract ")) mods.add("abstract");
        if (trimmed.contains("final ")) mods.add("final");
        if (trimmed.contains("static ")) mods.add("static");
        return mods.isEmpty() ? "" : String.join(" ", mods) + " ";
    }

    private boolean isIgnored(@NotNull VirtualFile file) {
        String name = file.getName();
        return name.startsWith(".") || name.equals("node_modules")
                || name.equals("build") || name.equals("target")
                || name.equals("__pycache__") || name.equals(".git")
                || name.equals("out") || name.equals(".gradle");
    }
}
