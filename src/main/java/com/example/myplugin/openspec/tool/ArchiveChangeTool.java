package com.example.myplugin.openspec.tool;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentTool;
import com.example.myplugin.openspec.manager.OpenSpecManager;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

public class ArchiveChangeTool implements AgentTool {

    private final AgentContext context;

    public ArchiveChangeTool(AgentContext context) {
        this.context = context;
    }

    @Override
    public String name() { return "archive_change"; }

    @Override
    public String description() { return "Archive a completed change: merge delta specs into main specs and move to archive/."; }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
            .name(name())
            .description(description())
            .parameters(JsonObjectSchema.builder()
                .addProperty("name", JsonStringSchema.builder()
                    .description("Change name to archive")
                    .build())
                .build())
            .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        try {
            String changeName = arguments.get("name").getAsString();
            OpenSpecManager manager = new OpenSpecManager(java.nio.file.Path.of(context.getProject().getBasePath()));

            OpenSpecManager.ArchiveResult result = manager.archiveChange(changeName);

            StringBuilder sb = new StringBuilder();
            sb.append("Archived '").append(changeName).append("' as '").append(result.getArchiveName()).append("'\n");

            if (!result.getUpdatedSpecs().isEmpty()) {
                sb.append("\nUpdated specs:\n");
                for (String spec : result.getUpdatedSpecs()) {
                    sb.append("  + ").append(spec).append("\n");
                }
            }

            if (!result.getWarnings().isEmpty()) {
                sb.append("\nWarnings:\n");
                for (String warning : result.getWarnings()) {
                    sb.append("  ! ").append(warning).append("\n");
                }
            }

            return sb.toString();
        } catch (Exception e) {
            return "Error archiving change: " + e.getMessage();
        }
    }
}
