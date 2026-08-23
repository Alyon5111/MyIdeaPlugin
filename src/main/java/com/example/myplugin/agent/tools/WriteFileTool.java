package com.example.myplugin.agent.tools;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentTool;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class WriteFileTool implements AgentTool {

    private final AgentContext context;

    public WriteFileTool(AgentContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "write_file";
    }

    @Override
    public String description() {
        return "Create or overwrite a file with the given content. "
                + "Path is relative to the project root. Creates parent directories if needed.";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description(description())
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("path", "File path relative to project root")
                        .addStringProperty("content", "The full content to write to the file")
                        .required("path", "content")
                        .build())
                .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        String path = arguments.get("path").getAsString();
        String content = arguments.get("content").getAsString();
        Path filePath = context.getBaseDir().resolve(path);

        if (!filePath.startsWith(context.getBaseDir())) {
            return "Error: path escapes project directory";
        }

        try {
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, content, StandardCharsets.UTF_8);

            WriteAction.runAndWait(() -> {
                VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(filePath);
                if (vf != null) {
                    vf.refresh(false, false);
                }
            });

            return "File written successfully: " + path;
        } catch (IOException e) {
            return "Error writing file: " + e.getMessage();
        }
    }
}
