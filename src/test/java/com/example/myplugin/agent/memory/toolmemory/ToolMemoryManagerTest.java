package com.example.myplugin.agent.memory.toolmemory;

import com.example.myplugin.agent.memory.AgentMemoryService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolMemoryManagerTest {

    @Test
    void testRememberAndGetToolState() {
        AgentMemoryService memory = AgentMemoryService.inMemory();
        ToolMemoryManager manager = new ToolMemoryManager(memory);

        manager.rememberToolState("tdd", "cycle=GREEN");
        assertEquals("cycle=GREEN", manager.getToolState("tdd"));
    }

    @Test
    void testGetUnknownToolStateReturnsEmpty() {
        AgentMemoryService memory = AgentMemoryService.inMemory();
        ToolMemoryManager manager = new ToolMemoryManager(memory);

        assertEquals("", manager.getToolState("nonexistent"));
    }

    @Test
    void testGetAllToolStates() {
        AgentMemoryService memory = AgentMemoryService.inMemory();
        ToolMemoryManager manager = new ToolMemoryManager(memory);

        manager.rememberToolState("a", "state1");
        manager.rememberToolState("b", "state2");

        assertEquals(2, manager.getAllToolStates().size());
    }
}
