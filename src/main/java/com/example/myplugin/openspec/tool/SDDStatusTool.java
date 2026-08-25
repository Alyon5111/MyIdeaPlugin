package com.example.myplugin.openspec.tool;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentTool;
import com.example.myplugin.openspec.manager.OpenSpecManager;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

public class SDDStatusTool implements AgentTool {

    private final AgentContext context;

    public SDDStatusTool(AgentContext context) {
        this.context = context;
    }

    @Override
    public String name() { return "sdd_status"; }

    @Override
    public String description() { return "Show OpenSpec status: number of specs, active changes, and their progress."; }

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
                return "OpenSpec is not initialized in this project.\n" +
                       "Directory structure: openspec/specs/ and openspec/changes/\n" +
                       "Create your first change with create_change tool.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("OpenSpec Status\n");
            sb.append("==============\n\n");

            // Specs
            var specs = manager.listSpecs();
            sb.append("Specs: ").append(specs.size()).append("\n");
            int totalReqs = specs.stream().mapToInt(OpenSpecManager.SpecInfo::getRequirementCount).sum();
            sb.append("Total requirements: ").append(totalReqs).append("\n\n");

            // Changes
            var changes = manager.listChanges();
            sb.append("Active changes: ").append(changes.size()).append("\n");
            for (var change : changes) {
                sb.append("  - ").append(change.getName());
                if (change.getTotalTasks() > 0) {
                    sb.append(" [").append(change.getCompletedTasks())
                      .append("/").append(change.getTotalTasks()).append(" tasks]");
                }
                if (change.isComplete()) sb.append(" COMPLETE");
                sb.append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            return "Error getting status: " + e.getMessage();
        }
    }
}
