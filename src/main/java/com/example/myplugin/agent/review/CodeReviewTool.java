package com.example.myplugin.agent.review;

import com.example.myplugin.agent.AgentTool;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.util.List;

public class CodeReviewTool implements AgentTool {

    private final ReviewChecklist checklist;
    private final IssueTracker issueTracker;

    public CodeReviewTool() {
        this.checklist = new ReviewChecklist();
        this.issueTracker = new IssueTracker();
    }

    @Override
    public String name() {
        return "code_review";
    }

    @Override
    public String description() {
        return "Systematic code review with checklist, YAGNI check, and response analysis";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
            .name(name())
            .description(description())
            .parameters(JsonObjectSchema.builder()
                .addProperty("action", JsonStringSchema.builder()
                    .description("Action to perform: start_review, check_item, add_issue, fix_issue, check_yagni, analyze_response, get_summary")
                    .build())
                .addProperty("input", JsonStringSchema.builder()
                    .description("Input for the action (item number, issue details, feature name, response text)")
                    .build())
                .build())
            .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        try {
            String action = arguments.has("action") ? arguments.get("action").getAsString() : "";
            String input = arguments.has("input") ? arguments.get("input").getAsString() : "";

            switch (action.toLowerCase()) {
                case "start_review":
                    return startReview();
                case "check_item":
                    return checkItem(input);
                case "add_issue":
                    return addIssue(input);
                case "fix_issue":
                    return fixIssue(input);
                case "check_yagni":
                    return checkYagni(input);
                case "analyze_response":
                    return analyzeResponse(input);
                case "get_summary":
                    return getSummary();
                default:
                    return "Unknown action: " + action + ". Use: start_review, check_item, add_issue, fix_issue, check_yagni, analyze_response, get_summary";
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String startReview() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== CODE REVIEW STARTED ===\n\n");
        sb.append("Checklist items:\n");

        int i = 1;
        for (ReviewChecklist.CheckItem item : checklist.getItems()) {
            sb.append(i++).append(". [").append(item.getSeverity()).append("] ")
              .append(item.getDescription()).append("\n");
        }

        sb.append("\nUse 'check_item' to mark items as passed/failed.");
        return sb.toString();
    }

    private String checkItem(String input) {
        String[] parts = input.split(" ", 2);
        if (parts.length < 2) {
            return "Usage: check_item <item_number> <pass/fail> [notes]";
        }

        int itemNum;
        try {
            itemNum = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            return "Invalid item number: " + parts[0];
        }

        if (itemNum < 1 || itemNum > checklist.getItems().size()) {
            return "Item number out of range: " + itemNum;
        }

        ReviewChecklist.CheckItem item = checklist.getItems().get(itemNum - 1);
        String[] rest = parts[1].split(" ", 2);
        boolean passed = rest[0].toLowerCase().startsWith("p");
        item.setPassed(passed);

        if (rest.length > 1) {
            item.setNotes(rest[1]);
        }

        return "Item " + itemNum + " marked as " + (passed ? "PASSED" : "FAILED");
    }

    private String addIssue(String input) {
        String[] parts = input.split(" ", 3);
        if (parts.length < 3) {
            return "Usage: add_issue <critical/important/minor> <description> <location>";
        }

        ReviewChecklist.Severity severity;
        switch (parts[0].toLowerCase()) {
            case "critical": severity = ReviewChecklist.Severity.CRITICAL; break;
            case "important": severity = ReviewChecklist.Severity.IMPORTANT; break;
            case "minor": severity = ReviewChecklist.Severity.MINOR; break;
            default: return "Invalid severity: " + parts[0] + ". Use: critical, important, minor";
        }

        issueTracker.addIssue(severity, parts[1], parts[2]);
        return "Issue added: [" + severity + "] " + parts[1] + " at " + parts[2];
    }

    private String fixIssue(String input) {
        try {
            int issueNum = Integer.parseInt(input);
            if (issueNum < 1 || issueNum > issueTracker.getIssues().size()) {
                return "Issue number out of range: " + issueNum;
            }

            IssueTracker.Issue issue = issueTracker.getIssues().get(issueNum - 1);
            issue.setStatus(IssueTracker.Status.FIXED);
            return "Issue " + issueNum + " marked as FIXED";
        } catch (NumberFormatException e) {
            return "Invalid issue number: " + input;
        }
    }

    private String checkYagni(String feature) {
        if (ResponseAnalyzer.isYagniCandidate(feature)) {
            return ResponseAnalyzer.getYagniMessage(feature);
        }
        return "Feature does not appear to be a YAGNI candidate.";
    }

    private String analyzeResponse(String response) {
        List<String> performative = ResponseAnalyzer.detectPerformativeResponses(response);
        if (!performative.isEmpty()) {
            return ResponseAnalyzer.getPerformativeMessage(performative);
        }
        return "No performative responses detected. Response is technically appropriate.";
    }

    private String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(checklist.getSummary());
        sb.append("\n");
        sb.append(issueTracker.getSummary());
        return sb.toString();
    }
}
