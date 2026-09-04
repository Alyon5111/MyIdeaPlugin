package com.example.myplugin.agent.memory.toolmemory;

import com.example.myplugin.agent.memory.AgentMemoryService;
import com.example.myplugin.agent.memory.MemoryCategory;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MemoryStatusToolTest {

    private AgentMemoryService newMemory() {
        return AgentMemoryService.inMemory();
    }

    @Test
    void testName() {
        assertEquals("memory_status", new MemoryStatusTool(newMemory(), new ToolMemoryManager(newMemory())).name());
    }

    @Test
    void testSummary() {
        AgentMemoryService memory = newMemory();
        memory.addEntry(MemoryCategory.FACT, "a fact", "test", 0.8);
        MemoryStatusTool tool = new MemoryStatusTool(memory, new ToolMemoryManager(memory));

        JsonObject args = new JsonObject();
        args.addProperty("action", "summary");
        String result = tool.execute(args);
        assertTrue(result.contains("Total entries: 1"));
    }

    @Test
    void testList() {
        AgentMemoryService memory = newMemory();
        memory.addEntry(MemoryCategory.FACT, "fact one", "test", 0.8);
        memory.addEntry(MemoryCategory.CONVENTION, "convention one", "test", 0.9);
        MemoryStatusTool tool = new MemoryStatusTool(memory, new ToolMemoryManager(memory));

        JsonObject args = new JsonObject();
        args.addProperty("action", "list");
        String result = tool.execute(args);
        assertTrue(result.contains("fact one"));
        assertTrue(result.contains("convention one"));
    }

    @Test
    void testListEmpty() {
        AgentMemoryService memory = newMemory();
        MemoryStatusTool tool = new MemoryStatusTool(memory, new ToolMemoryManager(memory));

        JsonObject args = new JsonObject();
        args.addProperty("action", "list");
        String result = tool.execute(args);
        assertTrue(result.contains("empty"));
    }

    @Test
    void testListWithCategoryFilter() {
        AgentMemoryService memory = newMemory();
        memory.addEntry(MemoryCategory.FACT, "fact only", "test", 0.8);
        memory.addEntry(MemoryCategory.CONVENTION, "convention only", "test", 0.9);
        MemoryStatusTool tool = new MemoryStatusTool(memory, new ToolMemoryManager(memory));

        JsonObject args = new JsonObject();
        args.addProperty("action", "list");
        args.addProperty("category", "FACT");
        String result = tool.execute(args);
        assertTrue(result.contains("fact only"));
        assertFalse(result.contains("convention only"));
    }

    @Test
    void testToolStates() {
        AgentMemoryService memory = newMemory();
        ToolMemoryManager toolMemory = new ToolMemoryManager(memory);
        toolMemory.rememberToolState("brainstorm", "topic=API design");
        MemoryStatusTool tool = new MemoryStatusTool(memory, toolMemory);

        JsonObject args = new JsonObject();
        args.addProperty("action", "tool_states");
        String result = tool.execute(args);
        assertTrue(result.contains("brainstorm"));
    }
}
