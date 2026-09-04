package com.example.myplugin.agent.review;

import java.util.ArrayList;
import java.util.List;

public class ReviewChecklist {

    public enum Category {
        SECURITY,
        PERFORMANCE,
        MAINTAINABILITY,
        CORRECTNESS,
        TEST_COVERAGE
    }

    public enum Severity {
        CRITICAL,
        IMPORTANT,
        MINOR
    }

    public static class CheckItem {
        private final Category category;
        private final String description;
        private Severity severity;
        private boolean passed;
        private String notes;

        public CheckItem(Category category, String description, Severity severity) {
            this.category = category;
            this.description = description;
            this.severity = severity;
            this.passed = false;
            this.notes = "";
        }

        public Category getCategory() { return category; }
        public String getDescription() { return description; }
        public Severity getSeverity() { return severity; }
        public boolean isPassed() { return passed; }
        public String getNotes() { return notes; }

        public void setPassed(boolean passed) { this.passed = passed; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    private final List<CheckItem> items;

    public ReviewChecklist() {
        this.items = new ArrayList<>();
        initializeDefaultChecks();
    }

    private void initializeDefaultChecks() {
        items.add(new CheckItem(Category.SECURITY,
            "Input validation and sanitization", Severity.CRITICAL));
        items.add(new CheckItem(Category.SECURITY,
            "SQL injection prevention", Severity.CRITICAL));
        items.add(new CheckItem(Category.SECURITY,
            "XSS prevention", Severity.CRITICAL));
        items.add(new CheckItem(Category.SECURITY,
            "Authentication/authorization checks", Severity.CRITICAL));

        items.add(new CheckItem(Category.PERFORMANCE,
            "N+1 query detection", Severity.IMPORTANT));
        items.add(new CheckItem(Category.PERFORMANCE,
            "Memory leak prevention", Severity.IMPORTANT));
        items.add(new CheckItem(Category.PERFORMANCE,
            "Efficient algorithms (O(n) vs O(n²))", Severity.IMPORTANT));

        items.add(new CheckItem(Category.MAINTAINABILITY,
            "Code clarity and readability", Severity.MINOR));
        items.add(new CheckItem(Category.MAINTAINABILITY,
            "Proper error handling", Severity.IMPORTANT));
        items.add(new CheckItem(Category.MAINTAINABILITY,
            "Documentation and comments", Severity.MINOR));

        items.add(new CheckItem(Category.CORRECTNESS,
            "Edge case handling", Severity.IMPORTANT));
        items.add(new CheckItem(Category.CORRECTNESS,
            "Null/undefined handling", Severity.IMPORTANT));
        items.add(new CheckItem(Category.CORRECTNESS,
            "Type safety", Severity.IMPORTANT));

        items.add(new CheckItem(Category.TEST_COVERAGE,
            "Unit tests present", Severity.IMPORTANT));
        items.add(new CheckItem(Category.TEST_COVERAGE,
            "Edge cases tested", Severity.IMPORTANT));
        items.add(new CheckItem(Category.TEST_COVERAGE,
            "Integration tests if needed", Severity.MINOR));
    }

    public List<CheckItem> getItems() { return items; }

    public List<CheckItem> getItemsByCategory(Category category) {
        List<CheckItem> filtered = new ArrayList<>();
        for (CheckItem item : items) {
            if (item.getCategory() == category) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    public List<CheckItem> getItemsBySeverity(Severity severity) {
        List<CheckItem> filtered = new ArrayList<>();
        for (CheckItem item : items) {
            if (item.getSeverity() == severity) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    public int getPassedCount() {
        int count = 0;
        for (CheckItem item : items) {
            if (item.isPassed()) count++;
        }
        return count;
    }

    public int getFailedCount() {
        int count = 0;
        for (CheckItem item : items) {
            if (!item.isPassed()) count++;
        }
        return count;
    }

    public boolean allCriticalPassed() {
        for (CheckItem item : items) {
            if (item.getSeverity() == Severity.CRITICAL && !item.isPassed()) {
                return false;
            }
        }
        return true;
    }

    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== REVIEW SUMMARY ===\n");
        sb.append("Total checks: ").append(items.size()).append("\n");
        sb.append("Passed: ").append(getPassedCount()).append("\n");
        sb.append("Failed: ").append(getFailedCount()).append("\n");
        sb.append("Critical passed: ").append(allCriticalPassed() ? "YES [OK]" : "NO [FAIL]").append("\n");
        return sb.toString();
    }
}
