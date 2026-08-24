package com.example.myplugin.agent.tools;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentTool;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GotoDefinitionTool implements AgentTool {

    private static final Logger LOG = Logger.getInstance(GotoDefinitionTool.class);
    private static final Pattern TYPE_DEF = Pattern.compile(
            "(?<![.\\w])(class|interface|enum|record)\\s+(\\w+)");
    private static final Pattern METHOD_DEF = Pattern.compile(
            "(?:^|\\s)(?:public |protected |private |static |final |abstract |synchronized |native |default )*"
                    + "[\\w<>\\[\\],\\s.?]+?\\s(\\w+)\\s*\\([^)]*\\)");
    private static final int MAX_RESULTS = 20;

    private final AgentContext context;

    public GotoDefinitionTool(AgentContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "goto_definition";
    }

    @Override
    public String description() {
        return "Go to the definition of the symbol at a given position in a file. "
                + "Resolves the IDE reference when possible; otherwise searches project declarations. "
                + "Returns the definition location (file and line number) plus a source snippet.";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description(description())
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("path", "File path relative to project root")
                        .addIntegerProperty("line", "Line number of the symbol (1-based)")
                        .addIntegerProperty("column", "Column of the symbol (1-based, optional, default 1)")
                        .required("path", "line")
                        .build())
                .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        if (!arguments.has("path") || arguments.get("path").isJsonNull()) {
            return "Error: missing required parameter 'path'";
        }
        if (!arguments.has("line") || arguments.get("line").isJsonNull()) {
            return "Error: missing required parameter 'line'";
        }
        String path = arguments.get("path").getAsString().trim();
        int line = arguments.get("line").getAsInt();
        int column = arguments.has("column") && !arguments.get("column").isJsonNull()
                ? arguments.get("column").getAsInt() : 1;

        Path filePath = context.getBaseDir().resolve(path).normalize();
        if (!filePath.startsWith(context.getBaseDir())) {
            return "Error: path escapes project directory";
        }
        if (!Files.exists(filePath)) {
            return "Error: file not found: " + path;
        }

        Project project = context.getProject();
        if (DumbService.isDumb(project)) {
            return "Error: IDE is indexing, please wait and try again";
        }

        try {
            return ReadAction.compute(() -> doGoto(project, filePath, path, line, column));
        } catch (Exception e) {
            LOG.warn("GotoDefinitionTool failed", e);
            return "Error resolving definition: " + e.getMessage();
        }
    }

    private String doGoto(@NotNull Project project, @NotNull Path filePath,
                          @NotNull String path, int line, int column) {
        VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(filePath);
        if (vf == null) {
            return "Error: cannot open file in IDE: " + path;
        }
        PsiFile psiFile = PsiManager.getInstance(project).findFile(vf);
        if (psiFile == null) {
            return "Error: unsupported file type: " + path;
        }
        Document doc = FileDocumentManager.getInstance().getDocument(vf);
        if (doc == null) {
            return "Error: cannot read document for " + path;
        }
        if (line < 1 || line > doc.getLineCount()) {
            return "Error: line " + line + " out of range (file has " + doc.getLineCount() + " lines)";
        }

        int lineStart = doc.getLineStartOffset(line - 1);
        int lineEnd = doc.getLineEndOffset(line - 1);
        int offset = Math.min(lineStart + Math.max(0, column - 1), lineEnd);
        if (offset == lineEnd && offset > lineStart) {
            offset--;
        }

        PsiReference ref = psiFile.findReferenceAt(offset);
        if (ref != null) {
            PsiElement resolved = ref.resolve();
            if (resolved != null) {
                return describeTarget(resolved);
            }
        }

        String text = doc.getText();
        int start = offset;
        while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) start--;
        int end = offset;
        while (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end))) end++;
        if (end <= start) {
            return "No symbol found at " + path + ":" + line + ":" + column;
        }
        String word = text.substring(start, end);
        List<String> matches = searchDeclarations(word);
        if (matches.isEmpty()) {
            return "No definition found for '" + word
                    + "' in the project. It may be declared in a library dependency.";
        }
        StringBuilder sb = new StringBuilder("Candidate definitions for '").append(word).append("':\n\n");
        for (String m : matches) {
            sb.append(m).append('\n');
        }
        return sb.toString();
    }

    private String describeTarget(@NotNull PsiElement element) {
        PsiElement target = element.getNavigationElement() != null
                ? element.getNavigationElement() : element;
        PsiFile file = target.getContainingFile();
        if (file == null || file.getVirtualFile() == null) {
            String text = target.getText();
            if (text != null && text.length() > 300) {
                text = text.substring(0, 300) + "...";
            }
            return "Resolved to a library element (no project source):\n" + text;
        }
        VirtualFile targetVf = file.getVirtualFile();
        Document targetDoc = FileDocumentManager.getInstance().getDocument(targetVf);
        int lineNumber = -1;
        if (targetDoc != null) {
            int textOffset = Math.min(target.getTextOffset(), targetDoc.getTextLength());
            lineNumber = targetDoc.getLineNumber(textOffset) + 1;
        }

        StringBuilder sb = new StringBuilder("Definition found:\n");
        sb.append("File: ").append(toRelativePath(targetVf)).append('\n');
        sb.append("Line: ").append(lineNumber).append('\n');

        String snippet = target.getText();
        if (snippet != null && !snippet.isBlank()) {
            snippet = snippet.replaceAll("\\s+", " ").trim();
            if (snippet.length() > 400) {
                snippet = snippet.substring(0, 400) + "...";
            }
            sb.append("Symbol: ").append(snippet);
        }
        return sb.toString();
    }

    private List<String> searchDeclarations(String word) {
        List<String> results = new ArrayList<>();
        VirtualFile baseDir = LocalFileSystem.getInstance()
                .findFileByPath(context.getBaseDir().toAbsolutePath().toString());
        if (baseDir == null) {
            return results;
        }

        VfsUtil.visitChildrenRecursively(baseDir, new VirtualFileVisitor<>() {
            @Override
            public boolean visitFile(@NotNull VirtualFile file) {
                if (results.size() >= MAX_RESULTS) return false;
                if (isIgnored(file)) return true;
                String name = file.getName();
                if (!name.endsWith(".java") && !name.endsWith(".kt")) return true;

                try {
                    byte[] bytes = file.contentsToByteArray();
                    String content = new String(bytes, StandardCharsets.UTF_8);
                    String[] lines = content.split("\n");
                    String relPath = VfsUtil.getRelativePath(file, baseDir, '/');
                    if (relPath == null) relPath = file.getPath();

                    for (int i = 0; i < lines.length && results.size() < MAX_RESULTS; i++) {
                        String trimmed = lines[i].trim();
                        Matcher tm = TYPE_DEF.matcher(trimmed);
                        if (tm.find() && tm.group(2).equals(word)) {
                            results.add(relPath + ":" + (i + 1) + ": type declaration: " + trimmed);
                            continue;
                        }
                        Matcher mm = METHOD_DEF.matcher(trimmed);
                        if (mm.matches() && mm.group(1).equals(word)) {
                            results.add(relPath + ":" + (i + 1) + ": method declaration: " + trimmed);
                        }
                    }
                } catch (Exception ignored) {
                }
                return true;
            }
        });
        return results;
    }

    private String toRelativePath(@NotNull VirtualFile file) {
        String basePath = context.getBaseDir().toAbsolutePath().toString().replace('\\', '/');
        String path = file.getPath();
        return path.startsWith(basePath) ? path.substring(basePath.length() + 1) : path;
    }

    private boolean isIgnored(@NotNull VirtualFile file) {
        String name = file.getName();
        return name.startsWith(".") || name.equals("node_modules")
                || name.equals("build") || name.equals("target")
                || name.equals("__pycache__") || name.equals(".git")
                || name.equals("out") || name.equals(".gradle");
    }
}
