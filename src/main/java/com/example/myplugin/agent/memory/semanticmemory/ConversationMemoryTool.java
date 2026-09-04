package com.example.myplugin.agent.memory.semanticmemory;

import com.example.myplugin.agent.AgentTool;
import com.example.myplugin.agent.memory.AgentMemoryService;
import com.example.myplugin.agent.memory.MemoryCategory;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

public class ConversationMemoryTool implements AgentTool {

    private final ConversationSemanticMemory memory;

    public ConversationMemoryTool(AgentMemoryService memoryService) {
        this.memory = new ConversationSemanticMemory(memoryService);
    }

    @Override
    public String name() {
        return "conversation_memory";
    }

    @Override
    public String description() {
        return "Store and retrieve conversation-derived memory: facts, decisions, preferences, conventions from past chat sessions";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
            .name(name())
            .description(description())
            .parameters(JsonObjectSchema.builder()
                .addProperty("action", JsonStringSchema.builder()
                    .description("Action: search, remember_fact, remember_decision, remember_preference, remember_convention")
                    .build())
                .addProperty("query", JsonStringSchema.builder()
                    .description("Search term (for action=search)")
                    .build())
                .addProperty("content", JsonStringSchema.builder()
                    .description("Content to remember")
                    .build())
                .build())
            .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        try {
            String action = arguments.has("action") ? arguments.get("action").getAsString() : "search";
            String content = arguments.has("content") ? arguments.get("content").getAsString() : "";
            String query = arguments.has("query") ? arguments.get("query").getAsString() : "";

            switch (action.toLowerCase()) {
                case "search":
                    return memory.search(query);
                case "remember_fact":
                    return remember(content, MemoryCategory.FACT);
                case "remember_decision":
                    return remember(content, MemoryCategory.DECISION);
                case "remember_preference":
                    return remember(content, MemoryCategory.PREFERENCE);
                case "remember_convention":
                    return remember(content, MemoryCategory.CONVENTION);
                default:
                    return "Unknown action: " + action + ". Use: search, remember_fact, remember_decision, remember_preference, remember_convention";
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String remember(String content, MemoryCategory category) {
        if (content.isEmpty()) {
            return "Error: 'content' is required";
        }
        memory.record(content, category, "agent-tool");
        return "Remembered [" + category + "]: " + content;
    }
}
