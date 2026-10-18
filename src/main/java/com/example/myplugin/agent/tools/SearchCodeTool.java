package com.example.myplugin.agent.tools;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentTool;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class SearchCodeTool implements AgentTool {

    private static final Logger LOG = Logger.getInstance(SearchCodeTool.class);
    private final AgentContext context;

    public SearchCodeTool(AgentContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "search_code";
    }

    @Override
    public String description() {
        return "Search for a regex pattern across project source files. "
                + "Returns matching lines with file path and line number. "
                + "Uses IntelliJ's file index for accurate results. "
                + "Optionally filter by file extension (e.g. \"*.java\").";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description(description())
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("pattern", "Java regex pattern to search for")
                        .addStringProperty("file_pattern", "Glob pattern to filter files, e.g. \"*.java\" (optional)")
                        .required("pattern")
                        .build())
                .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        String patternStr = arguments.get("pattern").getAsString();
        String filePattern = arguments.has("file_pattern") && !arguments.get("file_pattern").isJsonNull()
                ? arguments.get("file_pattern").getAsString() : null;

        Pattern pattern;
        try {
            pattern = Pattern.compile(patternStr, Pattern.MULTILINE);
        } catch (PatternSyntaxException e) {
            return "Error: invalid regex pattern: " + e.getMessage();
        }

        Project project = context.getProject();

        if (DumbService.isDumb(project)) {
            return "Error: IDE is indexing, please wait and try again";
        }

        try {
            return ReadAction.compute(() -> doSearch(project, pattern, filePattern));
        } catch (Exception e) {
            LOG.warn("SearchCodeTool search failed", e);
            return "Error searching code: " + e.getMessage();
        }
    }

    @NotNull
    private String doSearch(@NotNull Project project, @NotNull Pattern pattern, String filePattern) {
        List<String> results = new ArrayList<>();
        int maxResults = 100;

        String searchExtension = null;
        if (filePattern != null && !filePattern.isEmpty()) {
            searchExtension = filePattern.replace("*.", "").replace("*", "");
            if (searchExtension.startsWith(".")) {
                searchExtension = searchExtension.substring(1);
            }
        }
        final String ext = searchExtension;

        java.nio.file.Path baseDir = context.getBaseDir();
        VirtualFile baseVf =
                LocalFileSystem.getInstance()
                        .findFileByPath(baseDir.toAbsolutePath().toString());

        if (baseVf == null) {
            return "Error: cannot access project directory";
        }

        VfsUtil.visitChildrenRecursively(baseVf,
                new VirtualFileVisitor<>() {
                    @Override
                    public Result visitFileEx(@NotNull VirtualFile file) {
                        if (results.size() >= maxResults) {
                            return SKIP_CHILDREN;
                        }

                        // Prune build/ and .git/ subtrees here. The old code filtered those names
                        // only after "return true" for a directory had already been decided, so the
                        // exclusion never applied and stale artifacts under build/ (test-result XML
                        // in particular) flooded the results. SKIP_CHILDREN prunes exactly this
                        // subtree while the walk continues with its siblings.
                        if (file.isDirectory()) {
                            return excludedDirName(file.getName()) ? SKIP_CHILDREN : CONTINUE;
                        }
                        if (excludedDirName(file.getName())) {
                            return CONTINUE;
                        }

                        String name = file.getName().toLowerCase();
                        if (ext != null && !ext.isEmpty()) {
                            if (!name.endsWith("." + ext.toLowerCase())) return CONTINUE;
                        } else {
                            if (!isSearchableFile(name)) return CONTINUE;
                        }

                        try {
                            byte[] content = file.contentsToByteArray();
                            String text = new String(content, StandardCharsets.UTF_8);
                            String[] lines = text.split("\n");

                            String filePath = file.getPath();
                            String basePath = project.getBasePath();
                            String relPath = filePath.startsWith(basePath)
                                    ? filePath.substring(basePath.length() + 1).replace('\\', '/')
                                    : filePath;

                            for (int i = 0; i < lines.length; i++) {
                                if (results.size() >= maxResults) break;
                                if (pattern.matcher(lines[i]).find()) {
                                    results.add(String.format("%s:%d: %s",
                                            relPath, i + 1, lines[i].trim()));
                                }
                            }
                        } catch (Exception e) {
                            // Skip files that can't be read
                        }
                        return CONTINUE;
                    }
                });

        if (results.isEmpty()) {
            return "No matches found for pattern: " + pattern.pattern();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(results.size()).append(" match(es):\n\n");
        for (String line : results) {
            sb.append(line).append("\n");
        }
        if (results.size() >= maxResults) {
            sb.append("\n... (truncated at ").append(maxResults).append(" results)");
        }
        return sb.toString();
    }

    /**
     * Directories and files that must never be searched: build output and IDE/VCS metadata
     * produce matches that are pure noise (e.g. stale test-result XML under build/).
     */
    static boolean excludedDirName(String name) {
        String n = name.toLowerCase();
        return n.startsWith(".") || n.equals("node_modules") || n.equals("build")
                || n.equals("target") || n.equals("out") || n.equals("dist");
    }

    private boolean isSearchableFile(String name) {
        return name.endsWith(".java") || name.endsWith(".kt") || name.endsWith(".py")
                || name.endsWith(".js") || name.endsWith(".ts") || name.endsWith(".go")
                || name.endsWith(".rs") || name.endsWith(".c") || name.endsWith(".cpp")
                || name.endsWith(".h") || name.endsWith(".xml") || name.endsWith(".json")
                || name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".gradle")
                || name.endsWith(".kts") || name.endsWith(".properties") || name.endsWith(".md");
    }
}
