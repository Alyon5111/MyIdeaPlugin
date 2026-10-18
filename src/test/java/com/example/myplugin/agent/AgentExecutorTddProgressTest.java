package com.example.myplugin.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentExecutorTddProgressTest {

    @Test
    void writeEditAndTestRunsAreProgress() {
        assertTrue(AgentExecutor.tddProgressMade("write_file", "File written successfully: X", "RED"));
        assertTrue(AgentExecutor.tddProgressMade("edit_file", "File edited successfully: X", "GREEN"));
        assertTrue(AgentExecutor.tddProgressMade("run_tests", "TEST RUN FINISHED WITH EXIT CODE 1", "RED"));
        assertTrue(AgentExecutor.tddProgressMade("run_gradle_task", "BUILD SUCCESSFUL in 2s", "GREEN"));
    }

    @Test
    void failingTestRunsInGreenAreNotProgress() {
        assertFalse(AgentExecutor.tddProgressMade("run_tests", "TEST RUN FINISHED WITH EXIT CODE 1", "GREEN"));
        assertFalse(AgentExecutor.tddProgressMade("run_tests", "BUILD FAILED in 6s", "GREEN_VERIFY"));
        assertFalse(AgentExecutor.tddProgressMade("run_gradle_task", "Task 'test' finished with exit code 1", "GREEN"));
        assertTrue(AgentExecutor.tddProgressMade("run_tests", "TESTS PASSED\nBUILD SUCCESSFUL", "GREEN"));
    }

    @Test
    void rejectedToolsAreNotProgress() {
        assertFalse(AgentExecutor.tddProgressMade("tdd_enforcer", "Error: A cycle is already in progress (state: RED)", "RED"));
        assertFalse(AgentExecutor.tddProgressMade("write_file", "Error: file not found: X", "GREEN"));
        assertFalse(AgentExecutor.tddProgressMade("run_tests", "Error executing tool: exploded", "RED"));
    }

    @Test
    void getStatusIsNotProgress() {
        assertFalse(AgentExecutor.tddProgressMade("tdd_enforcer", "=== TDD Enforcer Status ===\nState: RED", "RED"));
    }

    @Test
    void advancingEnforcerActionsAreProgress() {
        assertTrue(AgentExecutor.tddProgressMade("tdd_enforcer", "=== TDD Cycle Started ===\nTask: t", "RED"));
        assertTrue(AgentExecutor.tddProgressMade("tdd_enforcer", "Test fails as expected. Now write minimal code.", "RED_VERIFY"));
        assertTrue(AgentExecutor.tddProgressMade("tdd_enforcer", "All tests pass. Now refactor.", "GREEN_VERIFY"));
        assertTrue(AgentExecutor.tddProgressMade("tdd_enforcer", "=== TDD Cycle Complete ===\nCycle #1 completed successfully.", "REFACTOR"));
    }

    @Test
    void nullResultOrUnknownToolIsNotProgress() {
        assertFalse(AgentExecutor.tddProgressMade("read_file", null, "GREEN"));
        assertFalse(AgentExecutor.tddProgressMade("read_file", "some file content", "GREEN"));
        assertFalse(AgentExecutor.tddProgressMade("list_files", "docs/", "RED"));
    }

    @Test
    void repeatCallNoteSaysResultIsUnchangedInsteadOfRepeatingIt() {
        String note = AgentExecutor.repeatCallNote("read_file", "package a;\nclass B {}\nrest of file");
        assertTrue(note.contains("[repeat call]"), note);
        assertTrue(note.contains("already executed earlier in this run"), note);
        assertTrue(note.contains("unchanged"), note);
        assertTrue(note.contains("first line: package a;"), note);
        assertFalse(note.contains("rest of file"), note);
    }

    @Test
    void stallIsReportedOnlyAfterEnoughIdleIterations() {
        assertFalse(AgentExecutor.shouldReportStall(0));
        assertFalse(AgentExecutor.shouldReportStall(3));
        assertTrue(AgentExecutor.shouldReportStall(4));
        assertTrue(AgentExecutor.shouldReportStall(9));
    }

    @Test
    void stallDigestReportsStateWrittenFilesAndRemainingBudget() {
        String digest = AgentExecutor.stallDigest("GREEN",
                java.util.List.of("src/test/java/XTest.java"),
                java.util.List.of("{\"path\":\"src/main/java/X.java\"}"),
                31, 4);
        assertTrue(digest.contains("NO PROGRESS FOR 4 ITERATIONS"), digest);
        assertTrue(digest.contains("Tool calls remaining: 31"), digest);
        assertTrue(digest.contains("TDD state: GREEN"), digest);
        assertTrue(digest.contains("src/test/java/XTest.java"), digest);
        assertTrue(digest.contains("get_status"), digest);
    }

    @Test
    void budgetExhaustionReportsIncompleteCycleNotAFinalAnswer() {
        String report = AgentExecutor.incompleteCycleReport("GREEN", java.util.List.of(), 48, 48, true);
        assertTrue(report.contains("TDD CYCLE NOT COMPLETED"), report);
        assertTrue(report.contains("Tool budget exhausted (48/48"), report);
        assertTrue(report.contains("TDD state: GREEN"), report);
        assertTrue(report.contains("Files written this run: none"), report);
        assertTrue(report.contains("do not report it as complete"), report);
        // The previous behaviour solicited prose, which turned an unfinished cycle into
        // plausible-looking unverified code.
        assertFalse(report.contains("Provide your final answer"), report);
    }

    @Test
    void iterationLimitExhaustionIsNotReportedAsToolBudgetExhaustion() {
        String report = AgentExecutor.incompleteCycleReport("RED", java.util.List.of(), 38, 48, false);
        assertTrue(report.contains("TDD CYCLE NOT COMPLETED"), report);
        assertTrue(report.contains("Iteration limit reached (38/48"), report);
        assertFalse(report.contains("Tool budget exhausted"), report);
        assertTrue(report.contains("TDD state: RED"), report);
    }

    @Test
    void budgetExhaustionListsWhatWasActuallyWritten() {
        String report = AgentExecutor.incompleteCycleReport("RED",
                java.util.List.of("src/test/java/XTest.java"), 48, 48, true);
        assertTrue(report.contains("src/test/java/XTest.java"), report);
        assertFalse(report.contains("Files written this run: none"), report);
    }
}