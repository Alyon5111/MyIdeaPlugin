package com.example.myplugin.agent.tools;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentTool;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class GetProjectStructureTool implements AgentTool {

    private static final Logger LOG = Logger.getInstance(GetProjectStructureTool.class);
    private static final Set<String> IGNORED_DIRS = Set.of(
            ".git", ".gradle", ".idea", "build", "target", "out", "node_modules",
            "__pycache__", ".venv", "venv", "dist");

    private final AgentContext context;

    public GetProjectStructureTool(AgentContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "get_project_structure";
    }

    @Override
    public String description() {
        return "Get an overview of the project structure: IDE modules with content roots, "
                + "detected build system files, top-level directories, and standard source directories. "
                + "Call this first when orienting in an unfamiliar project.";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description(description())
                .parameters(JsonObjectSchema.builder().build())
                .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        Project project = context.getProject();
        try {
            return ReadAction.compute(() -> describe(project));
        } catch (Exception e) {
            LOG.warn("GetProjectStructureTool failed", e);
            return "Error reading project structure: " + e.getMessage();
        }
    }

    private String describe(Project project) {
        StringBuilder sb = new StringBuilder();
        sb.append("Project: ").append(project.getName()).append("\n\n");

        sb.append("IDE modules:\n");
        Module[] modules = ModuleManager.getInstance(project).getModules();
        if (modules.length == 0) {
            sb.append("  (none)\n");
        }
        for (Module module : modules) {
            sb.append("  - ").append(module.getName()).append('\n');
            try {
                VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
                for (VirtualFile root : contentRoots) {
                    sb.append("      content root: ").append(toRelative(root)).append('\n');
                }
            } catch (Exception ignored) {
            }
        }
        if (modules.length == 0 || sb.indexOf("content root:") < 0) {
            sb.append("  base dir: .\n");
        }

        sb.append('\n').append("Build system:\n");
        String[] buildFiles = {
                "build.gradle.kts", "build.gradle", "settings.gradle.kts", "settings.gradle",
                "pom.xml", "gradlew.bat", "gradlew", "mvnw.cmd", "mvnw",
                "package.json", "go.mod", "Cargo.toml"
        };
        boolean foundAny = false;
        for (String name : buildFiles) {
            if (Files.exists(context.getBaseDir().resolve(name))) {
                sb.append("  ").append(name).append('\n');
                foundAny = true;
            }
        }
        if (!foundAny) {
            sb.append("  (no known build files at project root)\n");
        }

        sb.append('\n').append("Top-level entries:\n");
        List<Path> entries = listTopLevel();
        if (entries.isEmpty()) {
            sb.append("  (empty)\n");
        }
        for (Path entry : entries) {
            sb.append("  ").append(entry.getFileName())
                    .append(Files.isDirectory(entry) ? "/" : "").append('\n');
        }

        sb.append('\n').append("Source layout:\n");
        String[] sourceDirs = {
                "src/main/java", "src/main/resources", "src/test/java",
                "app/src/main/java", "lib/src/main/java", "src", "main", "test"
        };
        boolean foundSource = false;
        for (String dir : sourceDirs) {
            Path p = context.getBaseDir().resolve(dir);
            if (Files.isDirectory(p)) {
                sb.append("  ").append(dir).append("/\n");
                foundSource = true;
                if (dir.equals("src/main/java")) {
                    appendPackageRoots(sb, p);
                }
            }
        }
        if (!foundSource) {
            sb.append("  (no standard source directories found)\n");
        }
        return sb.toString();
    }

    private void appendPackageRoots(StringBuilder sb, Path javaDir) {
        try (Stream<Path> stream = Files.list(javaDir)) {
            List<Path> children = stream
                    .filter(p -> !IGNORED_DIRS.contains(p.getFileName().toString()))
                    .sorted()
                    .limit(30)
                    .toList();
            for (Path child : children) {
                sb.append("      ").append(child.getFileName().toString())
                        .append(Files.isDirectory(child) ? "/" : "").append('\n');
            }
            if (children.size() == 30) {
                sb.append("      ...\n");
            }
        } catch (IOException ignored) {
        }
    }

    private List<Path> listTopLevel() {
        try (Stream<Path> stream = Files.list(context.getBaseDir())) {
            return stream
                    .filter(p -> !IGNORED_DIRS.contains(p.getFileName().toString()))
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .sorted((a, b) -> {
                        int dirCompare = Boolean.compare(Files.isDirectory(b), Files.isDirectory(a));
                        return dirCompare != 0 ? dirCompare
                                : a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
                    })
                    .limit(40)
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private String toRelative(VirtualFile file) {
        String basePath = context.getBaseDir().toAbsolutePath().toString().replace('\\', '/');
        String path = file.getPath();
        return path.startsWith(basePath) ? path.substring(basePath.length() + 1) : path;
    }
}
