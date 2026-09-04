package com.example.myplugin.agent.memory.semanticmemory;

import com.example.myplugin.agent.memory.AgentMemoryService;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConversationMemoryToolTest {

    private ConversationMemoryTool newTool() {
        return new ConversationMemoryTool(AgentMemoryService.inMemory());
    }

    @Test
    void testName() {
        assertEquals("conversation_memory", newTool().name());
    }

    @Test
    void testRememberFact() {
        ConversationMemoryTool tool = newTool();
        JsonObject args = new JsonObject();
        args.addProperty("action", "remember_preference");
        args.addProperty("content", "User prefers dark theme");

        String result = tool.execute(args);
        assertTrue(result.contains("PREFERENCE"));
        assertTrue(result.contains("dark theme"));
    }

    @Test
    void testSearch() {
        ConversationMemoryTool tool = newTool();
        JsonObject remember = new JsonObject();
        remember.addProperty("action", "remember_fact");
        remember.addProperty("content", "Team uses Pair Programming");
        tool.execute(remember);

        JsonObject search = new JsonObject();
        search.addProperty("action", "search");
        search.addProperty("query", "Pair");

        String result = tool.execute(search);
        assertTrue(result.contains("Pair Programming"));
    }

    @Test
    void testSearchNoMatch() {
        ConversationMemoryTool tool = newTool();
        JsonObject search = new JsonObject();
        search.addProperty("action", "search");
        search.addProperty("query", "zzzznomatch");

        String result = tool.execute(search);
        assertTrue(result.contains("No semantic memory"));
    }

    @Test
    void testMissingContent() {
        ConversationMemoryTool tool = newTool();
        JsonObject args = new JsonObject();
        args.addProperty("action", "remember_fact");
        String result = tool.execute(args);
        assertTrue(result.contains("content"));
    }
}
