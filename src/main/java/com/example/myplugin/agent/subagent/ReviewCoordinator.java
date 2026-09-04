package com.example.myplugin.agent.subagent;

import java.util.ArrayList;
import java.util.List;

public class ReviewCoordinator {

    public enum ReviewStage {
        IDLE,
        SPEC_REVIEW,
        CODE_QUALITY_REVIEW,
        RE_REVIEW
    }

    public enum ReviewResult {
        PASS,
        FAIL,
        NEEDS_FIXES
    }

    private ReviewStage currentStage;
    private ReviewResult lastResult;
    private final List<String> issues;
    private final List<String> history;

    public ReviewCoordinator() {
        this.currentStage = ReviewStage.IDLE;
        this.issues = new ArrayList<>();
        this.history = new ArrayList<>();
    }

    public ReviewStage getCurrentStage() { return currentStage; }
    public ReviewResult getLastResult() { return lastResult; }
    public List<String> getIssues() { return issues; }
    public List<String> getHistory() { return history; }

    public void startSpecReview(String taskDescription) {
        this.currentStage = ReviewStage.SPEC_REVIEW;
        this.issues.clear();
        history.add("SPEC REVIEW STARTED: " + taskDescription);
    }

    public ReviewResult submitSpecReview(List<String> foundIssues) {
        if (currentStage != ReviewStage.SPEC_REVIEW) {
            return ReviewResult.FAIL;
        }

        issues.addAll(foundIssues);

        if (foundIssues.isEmpty()) {
            currentStage = ReviewStage.CODE_QUALITY_REVIEW;
            lastResult = ReviewResult.PASS;
            history.add("SPEC REVIEW: PASSED");
            return ReviewResult.PASS;
        } else {
            currentStage = ReviewStage.RE_REVIEW;
            lastResult = ReviewResult.NEEDS_FIXES;
            history.add("SPEC REVIEW: NEEDS FIXES (" + foundIssues.size() + " issues)");
            return ReviewResult.NEEDS_FIXES;
        }
    }

    public void startCodeQualityReview() {
        this.currentStage = ReviewStage.CODE_QUALITY_REVIEW;
        this.issues.clear();
        history.add("CODE QUALITY REVIEW STARTED");
    }

    public ReviewResult submitCodeQualityReview(List<String> foundIssues) {
        if (currentStage != ReviewStage.CODE_QUALITY_REVIEW) {
            return ReviewResult.FAIL;
        }

        issues.addAll(foundIssues);

        if (foundIssues.isEmpty()) {
            currentStage = ReviewStage.IDLE;
            lastResult = ReviewResult.PASS;
            history.add("CODE QUALITY REVIEW: PASSED");
            return ReviewResult.PASS;
        } else {
            currentStage = ReviewStage.RE_REVIEW;
            lastResult = ReviewResult.NEEDS_FIXES;
            history.add("CODE QUALITY REVIEW: NEEDS FIXES (" + foundIssues.size() + " issues)");
            return ReviewResult.NEEDS_FIXES;
        }
    }

    public void resetForFix() {
        if (currentStage == ReviewStage.RE_REVIEW) {
            history.add("RESET: Implementer fixing issues");
        }
    }

    public boolean isComplete() {
        return currentStage == ReviewStage.IDLE && lastResult == ReviewResult.PASS;
    }

    public String getStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== REVIEW STATUS ===\n");
        sb.append("Stage: ").append(currentStage).append("\n");
        sb.append("Last Result: ").append(lastResult).append("\n");
        sb.append("Issues: ").append(issues.size()).append("\n");
        if (!issues.isEmpty()) {
            sb.append("Issues:\n");
            for (String issue : issues) {
                sb.append("- ").append(issue).append("\n");
            }
        }
        return sb.toString();
    }
}
