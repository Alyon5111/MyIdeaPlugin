package com.example.myplugin.agent.plans;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskTrackerTest {

    @Test
    void testInitialState() {
        TaskTracker tracker = new TaskTracker();
        assertTrue(tracker.getTasks().isEmpty());
        assertEquals(0, tracker.getCompletedCount());
        assertEquals(0, tracker.getBlockedCount());
    }

    @Test
    void testAddTask() {
        TaskTracker tracker = new TaskTracker();
        tracker.addTask("Task 1");
        tracker.addTask("Task 2");

        assertEquals(2, tracker.getTasks().size());
        assertEquals("Task 1", tracker.getTasks().get(0).getDescription());
    }

    @Test
    void testMarkInProgress() {
        TaskTracker tracker = new TaskTracker();
        tracker.addTask("Task 1");

        tracker.markInProgress(1);
        assertEquals(TaskTracker.Status.IN_PROGRESS, tracker.getTask(1).getStatus());
        assertNotNull(tracker.getInProgressTask());
    }

    @Test
    void testMarkCompleted() {
        TaskTracker tracker = new TaskTracker();
        tracker.addTask("Task 1");

        tracker.markInProgress(1);
        tracker.markCompleted(1);

        assertEquals(TaskTracker.Status.COMPLETED, tracker.getTask(1).getStatus());
        assertEquals(1, tracker.getCompletedCount());
    }

    @Test
    void testMarkBlocked() {
        TaskTracker tracker = new TaskTracker();
        tracker.addTask("Task 1");

        tracker.markInProgress(1);
        tracker.markBlocked(1, "Dependency missing");

        assertEquals(TaskTracker.Status.BLOCKED, tracker.getTask(1).getStatus());
        assertEquals(1, tracker.getBlockedCount());
        assertEquals("Dependency missing", tracker.getTask(1).getNotes());
    }

    @Test
    void testGetNextPendingTask() {
        TaskTracker tracker = new TaskTracker();
        tracker.addTask("Task 1");
        tracker.addTask("Task 2");
        tracker.addTask("Task 3");

        tracker.markInProgress(1);
        tracker.markCompleted(1);

        TaskTracker.Task next = tracker.getNextPendingTask();
        assertNotNull(next);
        assertEquals(2, next.getId());
    }

    @Test
    void testAllCompleted() {
        TaskTracker tracker = new TaskTracker();
        tracker.addTask("Task 1");
        tracker.addTask("Task 2");

        tracker.markInProgress(1);
        tracker.markCompleted(1);
        tracker.markInProgress(2);
        tracker.markCompleted(2);

        assertTrue(tracker.allCompleted());
    }

    @Test
    void testGetSummary() {
        TaskTracker tracker = new TaskTracker();
        tracker.addTask("Task 1");
        tracker.addTask("Task 2");

        tracker.markInProgress(1);
        tracker.markCompleted(1);

        String summary = tracker.getSummary();
        assertTrue(summary.contains("TASK SUMMARY"));
        assertTrue(summary.contains("Completed: 1"));
    }
}
