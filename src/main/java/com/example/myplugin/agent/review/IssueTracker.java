package com.example.myplugin.agent.review;

import java.util.ArrayList;
import java.util.List;

public class IssueTracker {

    public enum Status {
        OPEN,
        FIXED,
        IGNORED,
        DEFERRED
    }

    public static class Issue {
        private final ReviewChecklist.Severity severity;
        private final String description;
        private final String location;
        private Status status;
        private String notes;

        public Issue(ReviewChecklist.Severity severity, String description, String location) {
            this.severity = severity;
            this.description = description;
            this.location = location;
            this.status = Status.OPEN;
            this.notes = "";
        }

        public ReviewChecklist.Severity getSeverity() { return severity; }
        public String getDescription() { return description; }
        public String getLocation() { return location; }
        public Status getStatus() { return status; }
        public String getNotes() { return notes; }

        public void setStatus(Status status) { this.status = status; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    private final List<Issue> issues;

    public IssueTracker() {
        this.issues = new ArrayList<>();
    }

    public void addIssue(ReviewChecklist.Severity severity, String description, String location) {
        issues.add(new Issue(severity, description, location));
    }

    public List<Issue> getIssues() { return issues; }

    public List<Issue> getIssuesByStatus(Status status) {
        List<Issue> filtered = new ArrayList<>();
        for (Issue issue : issues) {
            if (issue.getStatus() == status) {
                filtered.add(issue);
            }
        }
        return filtered;
    }

    public List<Issue> getIssuesBySeverity(ReviewChecklist.Severity severity) {
        List<Issue> filtered = new ArrayList<>();
        for (Issue issue : issues) {
            if (issue.getSeverity() == severity) {
                filtered.add(issue);
            }
        }
        return filtered;
    }

    public int getOpenCount() { return getIssuesByStatus(Status.OPEN).size(); }
    public int getFixedCount() { return getIssuesByStatus(Status.FIXED).size(); }

    public boolean allCriticalFixed() {
        for (Issue issue : issues) {
            if (issue.getSeverity() == ReviewChecklist.Severity.CRITICAL &&
                issue.getStatus() == Status.OPEN) {
                return false;
            }
        }
        return true;
    }

    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ISSUE SUMMARY ===\n");
        sb.append("Total issues: ").append(issues.size()).append("\n");
        sb.append("Open: ").append(getOpenCount()).append("\n");
        sb.append("Fixed: ").append(getFixedCount()).append("\n");
        sb.append("Critical fixed: ").append(allCriticalFixed() ? "YES [OK]" : "NO [FAIL]").append("\n");
        return sb.toString();
    }
}
