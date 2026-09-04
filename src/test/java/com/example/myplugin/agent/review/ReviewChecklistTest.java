package com.example.myplugin.agent.review;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReviewChecklistTest {

    @Test
    void testInitialization() {
        ReviewChecklist checklist = new ReviewChecklist();
        assertFalse(checklist.getItems().isEmpty());
        assertTrue(checklist.getItems().size() > 10);
    }

    @Test
    void testGetItemsByCategory() {
        ReviewChecklist checklist = new ReviewChecklist();
        List<ReviewChecklist.CheckItem> securityItems = checklist.getItemsByCategory(
            ReviewChecklist.Category.SECURITY);
        assertFalse(securityItems.isEmpty());
        for (ReviewChecklist.CheckItem item : securityItems) {
            assertEquals(ReviewChecklist.Category.SECURITY, item.getCategory());
        }
    }

    @Test
    void testGetItemsBySeverity() {
        ReviewChecklist checklist = new ReviewChecklist();
        List<ReviewChecklist.CheckItem> criticalItems = checklist.getItemsBySeverity(
            ReviewChecklist.Severity.CRITICAL);
        assertFalse(criticalItems.isEmpty());
        for (ReviewChecklist.CheckItem item : criticalItems) {
            assertEquals(ReviewChecklist.Severity.CRITICAL, item.getSeverity());
        }
    }

    @Test
    void testPassedCount() {
        ReviewChecklist checklist = new ReviewChecklist();
        assertEquals(0, checklist.getPassedCount());

        checklist.getItems().get(0).setPassed(true);
        assertEquals(1, checklist.getPassedCount());
    }

    @Test
    void testFailedCount() {
        ReviewChecklist checklist = new ReviewChecklist();
        int total = checklist.getItems().size();
        assertEquals(total, checklist.getFailedCount());

        checklist.getItems().get(0).setPassed(true);
        assertEquals(total - 1, checklist.getFailedCount());
    }

    @Test
    void testAllCriticalPassed() {
        ReviewChecklist checklist = new ReviewChecklist();
        assertFalse(checklist.allCriticalPassed());

        for (ReviewChecklist.CheckItem item : checklist.getItems()) {
            if (item.getSeverity() == ReviewChecklist.Severity.CRITICAL) {
                item.setPassed(true);
            }
        }
        assertTrue(checklist.allCriticalPassed());
    }

    @Test
    void testGetSummary() {
        ReviewChecklist checklist = new ReviewChecklist();
        String summary = checklist.getSummary();
        assertTrue(summary.contains("REVIEW SUMMARY"));
        assertTrue(summary.contains("Total checks:"));
    }
}
