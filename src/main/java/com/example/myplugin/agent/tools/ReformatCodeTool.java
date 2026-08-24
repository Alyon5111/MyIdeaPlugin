package com.example.myplugin.agent.tools;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentTool;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

import java.nio.file.Files;
import java.nio.file.Path;

public class ReformatCodeTool implements AgentTool {

    private static final Logger LOG = Logger.getInstance(ReformatCodeTool.class);
    private final AgentContext context;

    public ReformatCodeTool(AgentContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "reformat_code";
    }

    @Override
    public String description() {
        return "Reformat a file according to the project code style using the IDE's code formatter, "
                + "then save it to disk.";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description(description())
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("path", "File path relative to project root")
                        .required("path")
                        .build())
                .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        if (!arguments.has("path") || arguments.get("path").isJsonNull()) {
            return "Error: missing required parameter 'path'";
        }
        String path = arguments.get("path").getAsString().trim();
        Path filePath = context.getBaseDir().resolve(path).normalize();

        if (!filePath.startsWith(context.getBaseDir())) {
            return "Error: path escapes project directory";
        }
        if (!Files.exists(filePath)) {
            return "Error: file not found: " + path;
        }

        Project project = context.getProject();

        PsiFile psiFile = ReadAction.compute(() -> {
            VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(filePath);
            if (vf == null) return null;
            return PsiManager.getInstance(project).findFile(vf);
        });

        if (psiFile == null) {
            return "Error: cannot open file in IDE (unsupported file type?): " + path;
        }

        try {
            WriteCommandAction.runWriteCommandAction(project,
                    (Computable<PsiElement>) () -> CodeStyleManager.getInstance(project).reformat(psiFile));

            WriteAction.compute(() -> {
                Document doc = FileDocumentManager.getInstance().getDocument(psiFile.getVirtualFile());
                if (doc != null) {
                    FileDocumentManager.getInstance().saveDocument(doc);
                }
                return null;
            });

            long size = Files.size(filePath);
            return "Reformatted and saved " + path + " (" + size + " bytes)";
        } catch (Exception e) {
            LOG.warn("ReformatCodeTool failed", e);
            return "Error reformatting file: " + e.getMessage();
        }
    }
}
