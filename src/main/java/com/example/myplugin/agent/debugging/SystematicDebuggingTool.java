package com.example.myplugin.agent.debugging;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentTool;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.util.List;

public class SystematicDebuggingTool implements AgentTool {

    private final DebuggingStateMachine stateMachine;

    public SystematicDebuggingTool() {
        this.stateMachine = new DebuggingStateMachine();
    }

    @Override
    public String name() {
        return "systematic_debugging";
    }

    @Override
    public String description() {
        return "Enforces systematic debugging process: Root Cause -> Pattern -> Hypothesis -> Implementation";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
            .name(name())
            .description(description())
            .parameters(JsonObjectSchema.builder()
                .addProperty("action", JsonStringSchema.builder()
                    .description("Action to perform: start_investigation, submit_evidence, submit_pattern, submit_hypothesis, submit_fix, get_status, cancel")
                    .build())
                .addProperty("input", JsonStringSchema.builder()
                    .description("Input for the action (error messages, patterns, hypotheses, fix results)")
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
                case "start_investigation":
                    return startInvestigation(input);
                case "submit_evidence":
                    return submitEvidence(input);
                case "submit_pattern":
                    return submitPattern(input);
                case "submit_hypothesis":
                    return submitHypothesis(input);
                case "submit_fix":
                    return submitFix(input);
                case "get_status":
                    return getStatus();
                case "cancel":
                    return cancel();
                default:
                    return "Unknown action: " + action + ". Use: start_investigation, submit_evidence, submit_pattern, submit_hypothesis, submit_fix, get_status, cancel";
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String startInvestigation(String issue) {
        if (issue.isEmpty()) {
            return "Please describe the issue you're debugging.";
        }

        List<String> redFlags = RedFlagDetector.detectRedFlags(issue);
        if (!redFlags.isEmpty()) {
            return RedFlagDetector.getRedFlagMessage(redFlags);
        }

        stateMachine.startInvestigation(issue);
        return "=== SYSTEMATIC DEBUGGING STARTED ===\n\n" +
            "Issue: " + issue + "\n\n" +
            "Phase 1: ROOT CAUSE INVESTIGATION\n" +
            "----------------------------------\n" +
            "Before ANY fix, you must:\n" +
            "1. Read error messages carefully\n" +
            "2. Reproduce consistently\n" +
            "3. Check recent changes\n" +
            "4. Gather evidence\n\n" +
            "Provide error messages, stack traces, or reproduction steps.";
    }

    private String submitEvidence(String evidence) {
        if (stateMachine.isIdle()) {
            return "No active investigation. Use 'start_investigation' first.";
        }

        List<String> redFlags = RedFlagDetector.detectRedFlags(evidence);
        if (!redFlags.isEmpty()) {
            return RedFlagDetector.getRedFlagMessage(redFlags);
        }

        DebuggingStateMachine.TransitionResult result = stateMachine.transition(evidence);
        return formatResult(result);
    }

    private String submitPattern(String pattern) {
        if (stateMachine.isIdle()) {
            return "No active investigation. Use 'start_investigation' first.";
        }

        List<String> redFlags = RedFlagDetector.detectRedFlags(pattern);
        if (!redFlags.isEmpty()) {
            return RedFlagDetector.getRedFlagMessage(redFlags);
        }

        DebuggingStateMachine.TransitionResult result = stateMachine.transition(pattern);
        return formatResult(result);
    }

    private String submitHypothesis(String hypothesis) {
        if (stateMachine.isIdle()) {
            return "No active investigation. Use 'start_investigation' first.";
        }

        List<String> redFlags = RedFlagDetector.detectRedFlags(hypothesis);
        if (!redFlags.isEmpty()) {
            return RedFlagDetector.getRedFlagMessage(redFlags);
        }

        DebuggingStateMachine.TransitionResult result = stateMachine.transition(hypothesis);
        return formatResult(result);
    }

    private String submitFix(String fixResult) {
        if (stateMachine.isIdle()) {
            return "No active investigation. Use 'start_investigation' first.";
        }

        List<String> redFlags = RedFlagDetector.detectRedFlags(fixResult);
        if (!redFlags.isEmpty()) {
            return RedFlagDetector.getRedFlagMessage(redFlags);
        }

        DebuggingStateMachine.TransitionResult result = stateMachine.transition(fixResult);
        return formatResult(result);
    }

    private String getStatus() {
        if (stateMachine.isIdle()) {
            return "No active investigation.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== DEBUGGING STATUS ===\n");
        sb.append("State: ").append(stateMachine.getCurrentState()).append("\n");
        sb.append("Issue: ").append(stateMachine.getCurrentIssue()).append("\n");
        sb.append("Attempts: ").append(stateMachine.getAttemptCount()).append("/3\n");
        sb.append("\nEvidence collected:\n");
        for (String e : stateMachine.getEvidence()) {
            sb.append("- ").append(e).append("\n");
        }
        sb.append("\nAttempted fixes:\n");
        for (String f : stateMachine.getAttemptedFixes()) {
            sb.append("- ").append(f).append("\n");
        }
        return sb.toString();
    }

    private String cancel() {
        stateMachine.cancel();
        return "Investigation cancelled.";
    }

    private String formatResult(DebuggingStateMachine.TransitionResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== DEBUGGING UPDATE ===\n\n");

        if (result.isSuccess()) {
            sb.append("[OK] ").append(result.getMessage()).append("\n");
        } else {
            sb.append("[FAIL] ").append(result.getMessage()).append("\n");
        }

        sb.append("\nCurrent State: ").append(stateMachine.getCurrentState()).append("\n");
        sb.append("Attempts: ").append(stateMachine.getAttemptCount()).append("/3\n");

        return sb.toString();
    }
}
