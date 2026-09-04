package com.example.myplugin.openspec.tool;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentTool;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.nio.file.Path;

/**
 * Reads files from an OpenSpec change directory.
 */
public class ReadChangeTool implements AgentTool {

    private final AgentContext context;

    public ReadChangeTool(AgentContext context) {
        this.context = context;
    }

    @Override
    public String name() { return "read_change"; }

    @Override
    public String description() {
        return "Read files from an OpenSpec change. " +
               "Can read proposal.md, tasks.md, or delta spec files. " +
               "Returns the markdown content of the requested file.";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
            .name(name())
            .description(description())
            .parameters(JsonObjectSchema.builder()
                .addProperty("change_name", JsonStringSchema.builder()
                    .description("The change folder name (e.g. 'enhancement-plan')")
                    .build())
                .addProperty("file", JsonStringSchema.builder()
                    .description("The file to read: 'proposal', 'tasks', or a domain name for delta specs (e.g. 'agent-system')")
                    .build())
                .build())
            .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        try {
            String changeName = arguments.get("change_name").getAsString();
            String file = arguments.get("file").getAsString();

            Path basePath = Path.of(context.getProject().getBasePath());
            Path changeDir = basePath.resolve("openspec").resolve("changes").resolve(changeName);

            if (!java.nio.file.Files.exists(changeDir)) {
                return "Change not found: " + changeName;
            }

            Path targetFile;
            if ("proposal".equals(file)) {
                targetFile = changeDir.resolve("proposal.md");
            } else if ("tasks".equals(file)) {
                targetFile = changeDir.resolve("tasks.md");
            } else {
                // Assume it's a domain name for delta spec
                targetFile = changeDir.resolve("specs").resolve(file).resolve("spec.md");
            }

            if (!java.nio.file.Files.exists(targetFile)) {
                return "File not found: " + targetFile;
            }

            return java.nio.file.Files.readString(targetFile, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "Error reading change: " + e.getMessage();
        }
    }
}
