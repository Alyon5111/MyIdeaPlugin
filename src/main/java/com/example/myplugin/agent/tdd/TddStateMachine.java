package com.example.myplugin.agent.tdd;

import java.util.ArrayList;
import java.util.List;

public class TddStateMachine {

    public enum State {
        IDLE,
        RED,
        RED_VERIFY,
        GREEN,
        GREEN_VERIFY,
        REFACTOR,
        DONE,
        BLOCKED
    }

    private State currentState;
    private int cycleCount;
    private final List<String> history;
    private String currentTask;
    private String lastTestOutput;
    private String lastTestFailureReason;

    public TddStateMachine() {
        this.currentState = State.IDLE;
        this.cycleCount = 0;
        this.history = new ArrayList<>();
    }

    public State getCurrentState() { return currentState; }
    public int getCycleCount() { return cycleCount; }
    public List<String> getHistory() { return history; }
    public String getCurrentTask() { return currentTask; }
    public String getLastTestOutput() { return lastTestOutput; }
    public String getLastTestFailureReason() { return lastTestFailureReason; }

    public void startCycle(String task) {
        this.currentTask = task;
        this.currentState = State.RED;
        this.cycleCount++;
        history.add("CYCLE " + cycleCount + ": Started task '" + task + "' -> RED");
    }

    public TransitionResult transition(String input) {
        switch (currentState) {
            case IDLE:
                return new TransitionResult(false, "No active cycle. Use 'start_cycle' first.");

            case RED:
                history.add("RED: Writing failing test");
                currentState = State.RED_VERIFY;
                return new TransitionResult(true, "Write a failing test. Run it and report the output.");

            case RED_VERIFY:
                return handleRedVerify(input);

            case GREEN:
                history.add("GREEN: Writing minimal implementation");
                currentState = State.GREEN_VERIFY;
                return new TransitionResult(true, "Write minimal code to pass. Run tests and report output.");

            case GREEN_VERIFY:
                return handleGreenVerify(input);

            case REFACTOR:
                history.add("REFACTOR: Cleaning up code");
                currentState = State.GREEN_VERIFY;
                return new TransitionResult(true, "Refactoring done. Run tests to verify.");

            case DONE:
                return new TransitionResult(true, "Cycle complete. Start a new cycle with 'start_cycle'.");

            case BLOCKED:
                return new TransitionResult(false, "BLOCKED: " + lastTestOutput);

            default:
                return new TransitionResult(false, "Unknown state: " + currentState);
        }
    }

    private TransitionResult handleRedVerify(String testOutput) {
        lastTestOutput = testOutput;

        if (testOutput == null || testOutput.trim().isEmpty()) {
            return new TransitionResult(false, "Please provide test output.");
        }

        TestAnalyzer.Analysis analysis = TestAnalyzer.analyze(testOutput);

        switch (analysis.getOutcome()) {
            case TEST_FAILS_AS_EXPECTED:
                history.add("RED_VERIFY: Test fails as expected -> GREEN");
                currentState = State.GREEN;
                lastTestFailureReason = analysis.getReason();
                return new TransitionResult(true,
                    "Test fails as expected (" + analysis.getReason() + "). Now write minimal code to pass.");

            case TEST_PASSES:
                history.add("RED_VERIFY: Test PASSES - TDD cycle broken!");
                currentState = State.BLOCKED;
                lastTestOutput = "TDD VIOLATION: Test passes without implementation. Delete the test and rewrite it, " +
                    "or delete the implementation code you wrote before the test.";
                return new TransitionResult(false,
                    "TDD VIOLATION: Test passes without implementation! " +
                    "You must either:\n" +
                    "1. Delete the code you wrote before the test and start over\n" +
                    "2. Rewrite the test to actually test the missing functionality");

            case TEST_ERROR:
                history.add("RED_VERIFY: Test ERROR - " + analysis.getReason());
                currentState = State.RED;
                return new TransitionResult(false,
                    "Test has an error (not a proper failure): " + analysis.getReason() +
                    "\nFix the test so it fails for the right reason.");

            default:
                history.add("RED_VERIFY: Unclear outcome");
                currentState = State.RED;
                return new TransitionResult(false, "Could not determine test outcome. Please provide clearer output.");
        }
    }

    private TransitionResult handleGreenVerify(String testOutput) {
        lastTestOutput = testOutput;

        if (testOutput == null || testOutput.trim().isEmpty()) {
            return new TransitionResult(false, "Please provide test output.");
        }

        TestAnalyzer.Analysis analysis = TestAnalyzer.analyze(testOutput);

        switch (analysis.getOutcome()) {
            case TEST_PASSES:
                history.add("GREEN_VERIFY: Tests pass -> REFACTOR");
                currentState = State.REFACTOR;
                return new TransitionResult(true,
                    "All tests pass. Now refactor: remove duplicates, improve naming, extract helpers.");

            case TEST_FAILS_AS_EXPECTED:
                history.add("GREEN_VERIFY: Tests still fail");
                currentState = State.GREEN;
                return new TransitionResult(false,
                    "Tests still fail. Fix the implementation and run tests again.");

            case TEST_ERROR:
            case TEST_CRASH:
                history.add("GREEN_VERIFY: Test error");
                currentState = State.GREEN;
                return new TransitionResult(false,
                    "Test error: " + analysis.getReason() + ". Fix and retry.");

            default:
                currentState = State.GREEN;
                return new TransitionResult(false, "Could not determine outcome. Run tests again.");
        }
    }

    public void markRefactorDone() {
        history.add("REFACTOR_DONE: Cycle " + cycleCount + " complete");
        currentState = State.DONE;
    }

    public void cancel() {
        history.add("CANCELLED at state " + currentState);
        currentState = State.IDLE;
        currentTask = null;
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
