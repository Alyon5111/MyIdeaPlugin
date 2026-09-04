package com.example.myplugin.agent.subagent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubagentDispatcherTest {

    @Test
    void testInitialState() {
        SubagentDispatcher dispatcher = new SubagentDispatcher();
        assertTrue(dispatcher.getTasks().isEmpty());
        assertEquals(0, dispatcher.getCompletedCount());
    }

    @Test
    void testDispatchTask() {
        SubagentDispatcher dispatcher = new SubagentDispatcher();
        SubagentDispatcher.SubagentTask task = dispatcher.dispatchTask("Implement feature", "Context here");

        assertNotNull(task);
        assertEquals(1, task.getId());
        assertEquals("Implement feature", task.getDescription());
        assertEquals(SubagentDispatcher.SubagentStatus.DISPATCHED, task.getStatus());
    }

    @Test
    void testMarkRunning() {
        SubagentDispatcher dispatcher = new SubagentDispatcher();
        SubagentDispatcher.SubagentTask task = dispatcher.dispatchTask("Task 1", "Context");

        dispatcher.markRunning(1);
        assertEquals(SubagentDispatcher.SubagentStatus.RUNNING, task.getStatus());
    }

    @Test
    void testMarkCompleted() {
        SubagentDispatcher dispatcher = new SubagentDispatcher();
        SubagentDispatcher.SubagentTask task = dispatcher.dispatchTask("Task 1", "Context");

        dispatcher.markRunning(1);
        dispatcher.markCompleted(1, "Done");

        assertEquals(SubagentDispatcher.SubagentStatus.COMPLETED, task.getStatus());
        assertEquals("Done", task.getResult());
        assertEquals(1, dispatcher.getCompletedCount());
    }

    @Test
    void testMarkBlocked() {
        SubagentDispatcher dispatcher = new SubagentDispatcher();
        SubagentDispatcher.SubagentTask task = dispatcher.dispatchTask("Task 1", "Context");

        dispatcher.markRunning(1);
        dispatcher.markBlocked(1, "Missing dependency");

        assertEquals(SubagentDispatcher.SubagentStatus.BLOCKED, task.getStatus());
        assertEquals("Missing dependency", task.getResult());
    }

    @Test
    void testMarkNeedsContext() {
        SubagentDispatcher dispatcher = new SubagentDispatcher();
        SubagentDispatcher.SubagentTask task = dispatcher.dispatchTask("Task 1", "Context");

        dispatcher.markRunning(1);
        dispatcher.markNeedsContext(1, "What is the API endpoint?");

        assertEquals(SubagentDispatcher.SubagentStatus.NEEDS_CONTEXT, task.getStatus());
        assertEquals("What is the API endpoint?", task.getQuestions());
    }

    @Test
    void testAllCompleted() {
        SubagentDispatcher dispatcher = new SubagentDispatcher();
        dispatcher.dispatchTask("Task 1", "Context");
        dispatcher.dispatchTask("Task 2", "Context");

        dispatcher.markRunning(1);
        dispatcher.markCompleted(1, "Done");
        dispatcher.markRunning(2);
        dispatcher.markCompleted(2, "Done");

        assertTrue(dispatcher.allCompleted());
    }

    @Test
    void testGetSummary() {
        SubagentDispatcher dispatcher = new SubagentDispatcher();
        dispatcher.dispatchTask("Task 1", "Context");

        String summary = dispatcher.getSummary();
        assertTrue(summary.contains("SUBAGENT SUMMARY"));
        assertTrue(summary.contains("Total tasks: 1"));
    }
}
