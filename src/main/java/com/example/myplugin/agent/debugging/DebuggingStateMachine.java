package com.example.myplugin.agent.debugging;

import java.util.ArrayList;
import java.util.List;

public class DebuggingStateMachine {

    public enum State {
        IDLE,
        ROOT_CAUSE,
        PATTERN,
        HYPOTHESIS,
        IMPLEMENTATION,
        DONE,
        BLOCKED
    }

    private State currentState;
    private int attemptCount;
    private final List<String> history;
    private String currentIssue;
    private String rootCause;
    private String hypothesis;
    private final List<String> evidence;
    private final List<String> attemptedFixes;

    public DebuggingStateMachine() {
        this.currentState = State.IDLE;
        this.attemptCount = 0;
        this.history = new ArrayList<>();
        this.evidence = new ArrayList<>();
        this.attemptedFixes = new ArrayList<>();
    }

    public State getCurrentState() { return currentState; }
    public int getAttemptCount() { return attemptCount; }
    public List<String> getHistory() { return history; }
    public String getCurrentIssue() { return currentIssue; }
    public String getRootCause() { return rootCause; }
    public String getHypothesis() { return hypothesis; }
    public List<String> getEvidence() { return evidence; }
    public List<String> getAttemptedFixes() { return attemptedFixes; }

    public void startInvestigation(String issue) {
        this.currentIssue = issue;
        this.currentState = State.ROOT_CAUSE;
        this.attemptCount = 0;
        this.evidence.clear();
        this.attemptedFixes.clear();
        history.add("INVESTIGATION: Started for issue '" + issue + "' -> ROOT_CAUSE");
    }

    public TransitionResult transition(String input) {
        switch (currentState) {
            case IDLE:
                return new TransitionResult(false, "No active investigation. Use 'start_investigation' first.");

            case ROOT_CAUSE:
                return handleRootCause(input);

            case PATTERN:
                return handlePattern(input);

            case HYPOTHESIS:
                return handleHypothesis(input);

            case IMPLEMENTATION:
                return handleImplementation(input);

            case DONE:
                return new TransitionResult(true, "Investigation complete. Start a new investigation.");

            case BLOCKED:
                return new TransitionResult(false, "BLOCKED: Too many failed attempts. Question the architecture.");

            default:
                return new TransitionResult(false, "Unknown state: " + currentState);
        }
    }

    private TransitionResult handleRootCause(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new TransitionResult(false, "Please describe the issue or provide error messages.");
        }

        evidence.add("ISSUE: " + input);
        history.add("ROOT_CAUSE: Gathering evidence - " + input.substring(0, Math.min(50, input.length())));

        if (input.toLowerCase().contains("error") || input.toLowerCase().contains("exception") ||
            input.toLowerCase().contains("fail") || input.toLowerCase().contains("bug")) {
            currentState = State.PATTERN;
            return new TransitionResult(true,
                "Evidence collected. Now find similar working examples in the codebase.");
        }

        return new TransitionResult(true, "Please provide more details: error messages, stack traces, or reproduction steps.");
    }

    private TransitionResult handlePattern(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new TransitionResult(false, "Please describe similar working code or patterns.");
        }

        evidence.add("PATTERN: " + input);
        history.add("PATTERN: Found similar example - " + input.substring(0, Math.min(50, input.length())));
        currentState = State.HYPOTHESIS;
        return new TransitionResult(true,
            "Pattern identified. Now form a single hypothesis: 'I think X is the root cause because Y'");
    }

    private TransitionResult handleHypothesis(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new TransitionResult(false, "Please state your hypothesis.");
        }

        if (!input.toLowerCase().contains("because")) {
            return new TransitionResult(false,
                "Hypothesis must include 'because'. Format: 'I think X is the root cause because Y'");
        }

        this.hypothesis = input;
        evidence.add("HYPOTHESIS: " + input);
        history.add("HYPOTHESIS: " + input.substring(0, Math.min(50, input.length())));
        currentState = State.IMPLEMENTATION;
        return new TransitionResult(true,
            "Hypothesis recorded. Now test minimally: make the SMALLEST change to test it.");
    }

    private TransitionResult handleImplementation(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new TransitionResult(false, "Please describe the fix or test result.");
        }

        attemptedFixes.add(input);
        attemptCount++;
        history.add("IMPLEMENTATION: Attempt " + attemptCount + " - " + input.substring(0, Math.min(50, input.length())));

        if (input.toLowerCase().contains("pass") || input.toLowerCase().contains("success") ||
            input.toLowerCase().contains("fixed") || input.toLowerCase().contains("works")) {
            currentState = State.DONE;
            return new TransitionResult(true,
                "Fix verified! Issue resolved. Start a new investigation if needed.");
        }

        if (input.toLowerCase().contains("fail") || input.toLowerCase().contains("error") ||
            input.toLowerCase().contains("still broken")) {
            if (attemptCount >= 3) {
                currentState = State.BLOCKED;
                return new TransitionResult(false,
                    "BLOCKED: " + attemptCount + " failed attempts. STOP and question the architecture.\n" +
                    "Is this pattern fundamentally sound? Discuss with your human partner.");
            }
            currentState = State.ROOT_CAUSE;
            return new TransitionResult(false,
                "Fix didn't work. Return to ROOT_CAUSE phase. (" + attemptCount + "/3 attempts)");
        }

        return new TransitionResult(true, "Please report the test result: pass/fail/error.");
    }

    public void cancel() {
        history.add("CANCELLED at state " + currentState);
        currentState = State.IDLE;
        currentIssue = null;
        rootCause = null;
        hypothesis = null;
    }

    public boolean isBlocked() { return currentState == State.BLOCKED; }
    public boolean isIdle() { return currentState == State.IDLE; }
    public boolean isDone() { return currentState == State.DONE; }

    public static class TransitionResult {
        private final boolean success;
        private final String message;

        public TransitionResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
}
