package com.example.myplugin.openspec.tool;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentTool;
import com.example.myplugin.openspec.manager.OpenSpecManager;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

import java.util.List;

public class ListSpecsTool implements AgentTool {

    private final AgentContext context;

    public ListSpecsTool(AgentContext context) {
        this.context = context;
    }

    @Override
    public String name() { return "list_specs"; }

    @Override
    public String description() { return "List all OpenSpec spec files with their titles and requirement counts."; }

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
        try {
            OpenSpecManager manager = new OpenSpecManager(java.nio.file.Path.of(context.getProject().getBasePath()));
            if (!manager.isInitialized()) {
                return "OpenSpec not initialized. Run 'openspec init' first.";
            }

            List<OpenSpecManager.SpecInfo> specs = manager.listSpecs();
            if (specs.isEmpty()) {
                return "No specs found.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Specs (").append(specs.size()).append("):\n\n");
            for (OpenSpecManager.SpecInfo spec : specs) {
                sb.append("- ").append(spec.getName())
                  .append(": ").append(spec.getTitle())
                  .append(" (").append(spec.getRequirementCount()).append(" requirements)\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error listing specs: " + e.getMessage();
        }
    }
}
