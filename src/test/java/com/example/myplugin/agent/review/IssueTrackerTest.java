package com.example.myplugin.agent.review;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IssueTrackerTest {

    @Test
    void testInitialState() {
        IssueTracker tracker = new IssueTracker();
        assertTrue(tracker.getIssues().isEmpty());
        assertEquals(0, tracker.getOpenCount());
        assertEquals(0, tracker.getFixedCount());
    }

    @Test
    void testAddIssue() {
        IssueTracker tracker = new IssueTracker();
        tracker.addIssue(ReviewChecklist.Severity.CRITICAL, "SQL injection", "UserService.java:42");

        assertEquals(1, tracker.getIssues().size());
        assertEquals(1, tracker.getOpenCount());
    }

    @Test
    void testFixIssue() {
        IssueTracker tracker = new IssueTracker();
        tracker.addIssue(ReviewChecklist.Severity.CRITICAL, "SQL injection", "UserService.java:42");

        IssueTracker.Issue issue = tracker.getIssues().get(0);
        issue.setStatus(IssueTracker.Status.FIXED);

        assertEquals(0, tracker.getOpenCount());
        assertEquals(1, tracker.getFixedCount());
    }

    @Test
    void testGetIssuesByStatus() {
        IssueTracker tracker = new IssueTracker();
        tracker.addIssue(ReviewChecklist.Severity.CRITICAL, "Issue 1", "File1.java");
        tracker.addIssue(ReviewChecklist.Severity.IMPORTANT, "Issue 2", "File2.java");

        tracker.getIssues().get(0).setStatus(IssueTracker.Status.FIXED);

        assertEquals(1, tracker.getIssuesByStatus(IssueTracker.Status.OPEN).size());
        assertEquals(1, tracker.getIssuesByStatus(IssueTracker.Status.FIXED).size());
    }

    @Test
    void testGetIssuesBySeverity() {
        IssueTracker tracker = new IssueTracker();
        tracker.addIssue(ReviewChecklist.Severity.CRITICAL, "Issue 1", "File1.java");
        tracker.addIssue(ReviewChecklist.Severity.IMPORTANT, "Issue 2", "File2.java");
        tracker.addIssue(ReviewChecklist.Severity.CRITICAL, "Issue 3", "File3.java");

        assertEquals(2, tracker.getIssuesBySeverity(ReviewChecklist.Severity.CRITICAL).size());
        assertEquals(1, tracker.getIssuesBySeverity(ReviewChecklist.Severity.IMPORTANT).size());
    }

    @Test
    void testAllCriticalFixed() {
        IssueTracker tracker = new IssueTracker();
        tracker.addIssue(ReviewChecklist.Severity.CRITICAL, "Issue 1", "File1.java");
        tracker.addIssue(ReviewChecklist.Severity.IMPORTANT, "Issue 2", "File2.java");

        assertFalse(tracker.allCriticalFixed());

        tracker.getIssues().get(0).setStatus(IssueTracker.Status.FIXED);
        assertTrue(tracker.allCriticalFixed());
    }

    @Test
    void testGetSummary() {
        IssueTracker tracker = new IssueTracker();
        tracker.addIssue(ReviewChecklist.Severity.CRITICAL, "Issue 1", "File1.java");

        String summary = tracker.getSummary();
        assertTrue(summary.contains("ISSUE SUMMARY"));
        assertTrue(summary.contains("Total issues: 1"));
    }
}
