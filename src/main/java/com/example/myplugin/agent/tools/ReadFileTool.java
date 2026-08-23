package com.example.myplugin.agent.tools;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentTool;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReadFileTool implements AgentTool {

    private final AgentContext context;

    public ReadFileTool(AgentContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "read_file";
    }

    @Override
    public String description() {
        return "Read the content of a file. Returns the full file content. "
                + "Path is relative to the project root.";
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
        String path = arguments.get("path").getAsString();
        Path filePath = context.getBaseDir().resolve(path);

        if (!filePath.startsWith(context.getBaseDir())) {
            return "Error: path escapes project directory";
        }

        if (!Files.exists(filePath)) {
            return "Error: file not found: " + path;
        }

        if (!Files.isRegularFile(filePath)) {
            return "Error: not a file: " + path;
        }

        try {
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            if (content.length() > 50000) {
                content = content.substring(0, 50000) + "\n... (truncated)";
            }
            return content;
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }
}
