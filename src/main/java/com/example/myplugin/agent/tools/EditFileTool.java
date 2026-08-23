package com.example.myplugin.agent.tools;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentTool;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class EditFileTool implements AgentTool {

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
                + "Use this for targeted edits without rewriting the whole file. "
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

        try {
            String content = Files.readString(filePath, StandardCharsets.UTF_8);

            int count = countOccurrences(content, oldString);
            if (count == 0) {
                return "Error: old_string not found in " + path;
            }
            if (count > 1) {
                return "Error: old_string found " + count + " times in " + path + ". Must be unique. Provide more context.";
            }

            String updated = content.replace(oldString, newString);
            Files.writeString(filePath, updated, StandardCharsets.UTF_8);

            WriteAction.runAndWait(() -> {
                VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(filePath);
                if (vf != null) {
                    vf.refresh(false, false);
                }
            });

            return "File edited successfully: " + path;
        } catch (IOException e) {
            return "Error editing file: " + e.getMessage();
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
