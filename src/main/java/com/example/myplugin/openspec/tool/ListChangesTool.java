package com.example.myplugin.openspec.tool;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentTool;
import com.example.myplugin.openspec.manager.OpenSpecManager;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

import java.util.List;

public class ListChangesTool implements AgentTool {

    private final AgentContext context;

    public ListChangesTool(AgentContext context) {
        this.context = context;
    }

    @Override
    public String name() { return "list_changes"; }

    @Override
    public String description() { return "List all active OpenSpec changes with task progress."; }

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
                return "OpenSpec not initialized.";
            }

            List<OpenSpecManager.ChangeInfo> changes = manager.listChanges();
            if (changes.isEmpty()) {
                return "No active changes.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Active changes (").append(changes.size()).append("):\n\n");
            for (OpenSpecManager.ChangeInfo change : changes) {
                sb.append("- ").append(change.getName());
                if (change.getTotalTasks() > 0) {
                    sb.append(" [").append(change.getCompletedTasks())
                      .append("/").append(change.getTotalTasks()).append(" tasks]");
                }
                if (change.getDeltaSpecCount() > 0) {
                    sb.append(" (").append(change.getDeltaSpecCount()).append(" delta specs)");
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error listing changes: " + e.getMessage();
        }
    }
}
