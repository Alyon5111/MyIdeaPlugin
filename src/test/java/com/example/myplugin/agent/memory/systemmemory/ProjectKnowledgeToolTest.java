package com.example.myplugin.agent.memory.systemmemory;

import com.example.myplugin.agent.memory.AgentMemoryService;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProjectKnowledgeToolTest {

    private ProjectKnowledgeTool newTool() {
        return new ProjectKnowledgeTool(AgentMemoryService.inMemory());
    }

    @Test
    void testNameAndDescription() {
        ProjectKnowledgeTool tool = newTool();
        assertEquals("project_knowledge", tool.name());
        assertTrue(tool.description().toLowerCase().contains("tech stack"));
    }

    @Test
    void testRecordTechStack() {
        ProjectKnowledgeTool tool = newTool();
        JsonObject args = new JsonObject();
        args.addProperty("action", "record_tech_stack");
        args.addProperty("content", "Java 21 + Spring Boot");

        String result = tool.execute(args);
        assertTrue(result.contains("TECH_STACK"));
        assertTrue(result.contains("Java 21"));
    }

    @Test
    void testQueryFindsStoredKnowledge() {
        ProjectKnowledgeTool tool = newTool();
        JsonObject record = new JsonObject();
        record.addProperty("action", "record_convention");
        record.addProperty("content", "Use camelCase naming");
        tool.execute(record);

        JsonObject query = new JsonObject();
        query.addProperty("action", "query");
        query.addProperty("content", "camel");

        String result = tool.execute(query);
        assertTrue(result.contains("camelCase"));
    }

    @Test
    void testRecordDecision() {
        ProjectKnowledgeTool tool = newTool();
        JsonObject args = new JsonObject();
        args.addProperty("action", "record_decision");
        args.addProperty("subject", "Build tool");
        args.addProperty("content", "Use Gradle");

        String result = tool.execute(args);
        assertTrue(result.contains("DECISION"));
        assertTrue(result.contains("Gradle"));
    }

    @Test
    void testMissingContentReturnsError() {
        ProjectKnowledgeTool tool = newTool();
        JsonObject args = new JsonObject();
        args.addProperty("action", "record_fact");

        String result = tool.execute(args);
        assertTrue(result.contains("content"));
    }

    @Test
    void testGetAllKnowledge() {
        ProjectKnowledgeTool tool = newTool();
        JsonObject args = new JsonObject();
        args.addProperty("action", "get_all");
        String result = tool.execute(args);
        assertTrue(result.contains("PROJECT KNOWLEDGE"));
    }
}
