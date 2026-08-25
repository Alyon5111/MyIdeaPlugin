package com.example.myplugin.openspec.tool;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentTool;
import com.example.myplugin.openspec.manager.OpenSpecManager;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.nio.file.Path;

public class CreateChangeTool implements AgentTool {

    private final AgentContext context;

    public CreateChangeTool(AgentContext context) {
        this.context = context;
    }

    @Override
    public String name() { return "create_change"; }

    @Override
    public String description() { return "Create a new OpenSpec change with scaffolded proposal.md, tasks.md, and specs/ directory."; }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
            .name(name())
            .description(description())
            .parameters(JsonObjectSchema.builder()
                .addProperty("name", JsonStringSchema.builder()
                    .description("Change name (e.g. 'add-user-service')")
                    .build())
                .build())
            .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        try {
            String changeName = arguments.get("name").getAsString();
            OpenSpecManager manager = new OpenSpecManager(java.nio.file.Path.of(context.getProject().getBasePath()));

            if (!manager.isInitialized()) {
                manager.init();
            }

            Path changeDir = manager.createChange(changeName);
            return "Created change: " + changeName + "\n" +
                   "  proposal.md - describe why and what changes\n" +
                   "  tasks.md - implementation checklist\n" +
                   "  specs/ - delta spec files go here";
        } catch (Exception e) {
            return "Error creating change: " + e.getMessage();
        }
    }
}
