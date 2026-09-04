package com.example.myplugin.agent.memory.toolmemory;

import com.example.myplugin.agent.AgentTool;
import com.example.myplugin.agent.memory.AgentMemoryService;
import com.example.myplugin.agent.memory.MemoryEntry;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.util.List;

public class MemoryStatusTool implements AgentTool {

    private final AgentMemoryService memory;
    private final ToolMemoryManager toolMemory;

    public MemoryStatusTool(AgentMemoryService memoryService, ToolMemoryManager toolMemory) {
        this.memory = memoryService;
        this.toolMemory = toolMemory;
    }

    @Override
    public String name() {
        return "memory_status";
    }

    @Override
    public String description() {
        return "Show memory statistics, list all memories, or list tool states";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
            .name(name())
            .description(description())
            .parameters(JsonObjectSchema.builder()
                .addProperty("action", JsonStringSchema.builder()
                    .description("Action: summary, list, tool_states, prune")
                    .build())
                .addProperty("category", JsonStringSchema.builder()
                    .description("Optional category filter for list action")
                    .build())
                .build())
            .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        try {
            String action = arguments.has("action") ? arguments.get("action").getAsString() : "summary";
            String category = arguments.has("category") ? arguments.get("category").getAsString() : "";

            switch (action.toLowerCase()) {
                case "summary":
                    return memory.getSummary();
                case "list":
                    return listAll(category);
                case "tool_states":
                    return listToolStates();
                case "prune": {
                    memory.prune(memory.size() > 100 ? 100 : memory.size());
                    return "Pruned memory. Current size: " + memory.size();
                }
                default:
                    return "Unknown action: " + action + ". Use: summary, list, tool_states, prune";
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String listAll(String categoryFilter) {
        StringBuilder sb = new StringBuilder("=== ALL MEMORIES ===\n");
        List<MemoryEntry> entries = memory.getAllEntries();
        for (MemoryEntry e : entries) {
            if (!categoryFilter.isEmpty()
                    && !e.getCategory().name().equalsIgnoreCase(categoryFilter)
                    && !e.getCategory().name().toLowerCase().contains(categoryFilter.toLowerCase())) {
                continue;
            }
            sb.append("- [").append(e.getCategory()).append("] ").append(e.getContent())
              .append(" (conf ").append(String.format("%.2f", e.getConfidence())).append(")\n");
        }
        if (sb.toString().equals("=== ALL MEMORIES ===\n")) {
            sb.append("(empty)");
        }
        return sb.toString();
    }

    private String listToolStates() {
        StringBuilder sb = new StringBuilder("=== TOOL STATES ===\n");
        var states = toolMemory.getAllToolStates();
        if (states.isEmpty()) {
            sb.append("(none recorded)");
        } else {
            states.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
        }
        return sb.toString();
    }
}
