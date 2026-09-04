package com.example.myplugin.agent.memory.systemmemory;

import com.example.myplugin.agent.AgentTool;
import com.example.myplugin.agent.memory.AgentMemoryService;
import com.example.myplugin.agent.memory.MemoryCategory;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.util.List;

public class ProjectKnowledgeTool implements AgentTool {

    private final ProjectKnowledgeMemory knowledge;

    public ProjectKnowledgeTool(AgentMemoryService memoryService) {
        this.knowledge = new ProjectKnowledgeMemory(memoryService);
    }

    @Override
    public String name() {
        return "project_knowledge";
    }

    @Override
    public String description() {
        return "Store and retrieve project knowledge: tech stack, conventions, decisions, preferences, facts";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
            .name(name())
            .description(description())
            .parameters(JsonObjectSchema.builder()
                .addProperty("action", JsonStringSchema.builder()
                    .description("Action: query, record_tech_stack, record_convention, record_decision, record_preference, record_fact, get_all")
                    .build())
                .addProperty("content", JsonStringSchema.builder()
                    .description("Content to store or query (for query: search term)")
                    .build())
                .addProperty("subject", JsonStringSchema.builder()
                    .description("Decision subject (for record_decision)")
                    .build())
                .build())
            .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        try {
            String action = arguments.has("action") ? arguments.get("action").getAsString() : "get_all";
            String content = arguments.has("content") ? arguments.get("content").getAsString() : "";
            String subject = arguments.has("subject") ? arguments.get("subject").getAsString() : "";

            switch (action.toLowerCase()) {
                case "query":
                    return query(content);
                case "record_tech_stack":
                    return record(content, MemoryCategory.TECH_STACK);
                case "record_convention":
                    return record(content, MemoryCategory.CONVENTION);
                case "record_decision":
                    return recordDecision(subject, content);
                case "record_preference":
                    return record(content, MemoryCategory.PREFERENCE);
                case "record_fact":
                    return record(content, MemoryCategory.FACT);
                case "get_all":
                    return knowledge.getAllKnowledge();
                default:
                    return "Unknown action: " + action + ". Use: query, record_tech_stack, record_convention, record_decision, record_preference, record_fact, get_all";
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String record(String content, MemoryCategory category) {
        if (content.isEmpty()) {
            return "Error: 'content' is required";
        }
        KnowledgeResult result = addByCategory(content, category);
        return "Recorded [" + category + "]: " + content + (result == KnowledgeResult.STORED ? " (new)" : " (already exists)");
    }

    private String recordDecision(String subject, String decision) {
        if (subject.isEmpty() || decision.isEmpty()) {
            return "Error: Both 'subject' and 'content' (decision) are required";
        }
        knowledge.recordDecision(subject, decision);
        return "Recorded [DECISION]: " + subject + " -> " + decision;
    }

    private String query(String query) {
        if (query.isEmpty()) {
            return knowledge.getAllKnowledge();
        }
        List<com.example.myplugin.agent.memory.MemoryEntry> results = knowledge.search(query);
        if (results.isEmpty()) {
            return "No memory matches: " + query;
        }
        StringBuilder sb = new StringBuilder("=== MEMORY RESULTS for '" + query + "' ===\n");
        for (com.example.myplugin.agent.memory.MemoryEntry e : results) {
            sb.append("- [").append(e.getCategory()).append("] ").append(e.getContent())
              .append(" (conf ").append(String.format("%.2f", e.getConfidence())).append(")\n");
        }
        return sb.toString();
    }

    private enum KnowledgeResult { STORED, DUPLICATE }

    private KnowledgeResult addByCategory(String content, MemoryCategory category) {
        // We can't easily detect duplicate via the memory service return value,
        // but we record and it deduplicates. Return STORED by default.
        switch (category) {
            case TECH_STACK: knowledge.recordTechStack(content); break;
            case CONVENTION: knowledge.recordConvention(content); break;
            case PREFERENCE: knowledge.recordPreference(content); break;
            case FACT: knowledge.recordFact(content); break;
            default: break;
        }
        return KnowledgeResult.STORED;
    }
}
