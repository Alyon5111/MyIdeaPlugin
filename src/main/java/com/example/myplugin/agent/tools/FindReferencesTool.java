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

public class FindReferencesTool implements AgentTool {

    private static final Logger LOG = Logger.getInstance(FindReferencesTool.class);
    private static final Pattern CLASS_DEF = Pattern.compile(
            "(?:public\\s+)?(?:abstract\\s+)?(?:final\\s+)?(?:class|interface|enum|record)\\s+(\\w+)");
    private final AgentContext context;

    public FindReferencesTool(AgentContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "find_references";
    }

    @Override
    public String description() {
        return "Find all usages/references of a Java class or method in the project. "
                + "Searches file contents for import statements, method calls, and usages of the class/method name.";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description(description())
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("class_name", "Full or simple class name to find references for")
                        .addStringProperty("method_name", "Method name to find usages of (optional)")
                        .required("class_name")
                        .build())
                .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        String className = arguments.get("class_name").getAsString().trim();
        String methodName = arguments.has("method_name") && !arguments.get("method_name").isJsonNull()
                ? arguments.get("method_name").getAsString().trim() : null;
        Project project = context.getProject();

        if (DumbService.isDumb(project)) {
            return "Error: IDE is indexing, please wait and try again";
        }

        try {
            return ReadAction.compute(() -> doSearch(project, className, methodName));
        } catch (Exception e) {
            LOG.warn("FindReferencesTool search failed", e);
            return "Error searching references: " + e.getMessage();
        }
    }

    @NotNull
    private String doSearch(@NotNull Project project, @NotNull String className, String methodName) {
        VirtualFile baseDir = LocalFileSystem.getInstance().findFileByPath(context.getBaseDir().toAbsolutePath().toString());
        if (baseDir == null) {
            return "Error: cannot access project directory";
        }

        String searchTerm = methodName != null ? methodName : className;
        Pattern searchPattern = Pattern.compile("\\b" + Pattern.quote(searchTerm) + "\\b");
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
                    String relPath = VfsUtil.getRelativePath(file, baseDir, '/');
                    if (relPath == null) relPath = file.getPath();

                    for (int i = 0; i < lines.length; i++) {
                        if (results.size() >= maxResults) return false;
                        String line = lines[i];
                        if (line.trim().startsWith("//")) continue;

                        Matcher m = searchPattern.matcher(line);
                        if (m.find()) {
                            if (methodName == null) {
                                Matcher defMatcher = CLASS_DEF.matcher(line);
                                if (defMatcher.find() && defMatcher.group(1).equals(className)) continue;
                            }

                            String refType = classifyReference(line.trim());
                            results.add(String.format("%s:%d: [%s] %s",
                                    relPath, i + 1, refType, line.trim()));
                        }
                    }
                } catch (Exception ignored) {
                }
                return true;
            }
        });

        if (results.isEmpty()) {
            String searchTarget = methodName != null ? className + "." + methodName : className;
            return "No references found for " + searchTarget;
        }

        StringBuilder sb = new StringBuilder();
        String searchTarget = methodName != null ? className + "." + methodName : className;
        sb.append("Found ").append(results.size()).append(" reference(s) for ").append(searchTarget).append(":\n\n");
        for (String r : results) {
            sb.append(r).append("\n");
        }
        if (results.size() >= maxResults) {
            sb.append("\n... (truncated at ").append(maxResults).append(" results)");
        }
        return sb.toString();
    }

    @NotNull
    private String classifyReference(@NotNull String line) {
        if (line.startsWith("import ")) return "import";
        if (line.contains("(") && line.contains(")")) return "call";
        if (line.contains("@")) return "annotation";
        return "usage";
    }

    private boolean isIgnored(@NotNull VirtualFile file) {
        String name = file.getName();
        return name.startsWith(".") || name.equals("node_modules")
                || name.equals("build") || name.equals("target")
                || name.equals("__pycache__") || name.equals(".git")
                || name.equals("out") || name.equals(".gradle");
    }
}
