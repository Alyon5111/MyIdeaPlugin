package com.example.myplugin.agent.tools;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentTool;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.jetbrains.annotations.NotNull;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class EditFileTool implements AgentTool {

    private static final Logger LOG = Logger.getInstance(EditFileTool.class);
    private final AgentContext context;

    public EditFileTool(AgentContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "edit_file";
    }

    @Override
    public String description() {
        return "Replace an exact string match in a file with new content. "
                + "Uses IntelliJ's Document API with undo support. "
                + "The old_string must be an exact unique match in the file.";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description(description())
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("path", "File path relative to project root")
                        .addStringProperty("old_string", "Exact string to find and replace (must be unique in file)")
                        .addStringProperty("new_string", "Replacement string")
                        .required("path", "old_string", "new_string")
                        .build())
                .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        String path = arguments.get("path").getAsString();
        String oldString = arguments.get("old_string").getAsString();
        String newString = arguments.get("new_string").getAsString();
        Path filePath = context.getBaseDir().resolve(path);

        if (!filePath.startsWith(context.getBaseDir())) {
            return "Error: path escapes project directory";
        }

        if (!Files.exists(filePath)) {
            return "Error: file not found: " + path;
        }

        String updated;
        try {
            String content = Files.readString(filePath, StandardCharsets.UTF_8);

            int count = countOccurrences(content, oldString);
            if (count == 0) {
                return "Error: old_string not found in " + path;
            }
            if (count > 1) {
                return "Error: old_string found " + count + " times in " + path + ". Must be unique. Provide more context.";
            }

            updated = content.replace(oldString, newString);
            Files.writeString(filePath, updated, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("EditFileTool failed", e);
            return "Error editing file: " + e.getMessage();
        }

        syncOpenDocumentIfNeeded(filePath, updated);
        return "File edited successfully: " + path;
    }

    /**
     * If the file is currently open in the IDE, push the new bytes into its Document on the EDT
     * (from a background thread this would otherwise throw the EDT-only assertion).
     */
    private void syncOpenDocumentIfNeeded(Path filePath, String updated) {
        Application app = ApplicationManager.getApplication();
        if (app == null || app.isDisposed()) {
            return;
        }
        Runnable updater = () -> {
            VirtualFile vf = LocalFileSystem.getInstance().findFileByNioFile(filePath);
            if (vf == null) {
                return;
            }
            Document doc = FileDocumentManager.getInstance().getDocument(vf);
            if (doc == null) {
                return;
            }
            WriteCommandAction.runWriteCommandAction(context.getProject(), () -> {
                doc.setText(updated);
                FileDocumentManager.getInstance().saveDocument(doc);
            });
        };
        if (SwingUtilities.isEventDispatchThread()) {
            updater.run();
        } else {
            app.invokeAndWait(updater);
        }
    }

    private int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
