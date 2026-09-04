package com.example.myplugin.agent.branch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompletionHandlerTest {

    @Test
    void testInitialState() {
        CompletionHandler handler = new CompletionHandler();
        assertEquals(CompletionHandler.CompletionStatus.IDLE, handler.getStatus());
        assertFalse(handler.isTestsPass());
    }

    @Test
    void testVerifyTestsPass() {
        CompletionHandler handler = new CompletionHandler();
        handler.verifyTests(true);

        assertTrue(handler.isTestsPass());
        assertEquals(CompletionHandler.CompletionStatus.TESTS_VERIFIED, handler.getStatus());
        assertTrue(handler.canProceed());
    }

    @Test
    void testVerifyTestsFail() {
        CompletionHandler handler = new CompletionHandler();
        handler.verifyTests(false);

        assertFalse(handler.isTestsPass());
        assertEquals(CompletionHandler.CompletionStatus.IDLE, handler.getStatus());
        assertFalse(handler.canProceed());
    }

    @Test
    void testDetectEnvironment() {
        CompletionHandler handler = new CompletionHandler();
        handler.detectEnvironment("worktree");

        assertEquals("worktree", handler.getEnvironment());
        assertEquals(CompletionHandler.CompletionStatus.ENVIRONMENT_DETECTED, handler.getStatus());
    }

    @Test
    void testPresentOptions() {
        CompletionHandler handler = new CompletionHandler();
        handler.presentOptions();

        assertEquals(CompletionHandler.CompletionStatus.OPTIONS_PRESENTED, handler.getStatus());
    }

    @Test
    void testSelectOption() {
        CompletionHandler handler = new CompletionHandler();
        handler.selectOption("MERGE_LOCALLY");

        assertEquals(CompletionHandler.CompletionStatus.OPTION_SELECTED, handler.getStatus());
    }

    @Test
    void testStartExecution() {
        CompletionHandler handler = new CompletionHandler();
        handler.startExecution();

        assertEquals(CompletionHandler.CompletionStatus.EXECUTING, handler.getStatus());
    }

    @Test
    void testComplete() {
        CompletionHandler handler = new CompletionHandler();
        handler.complete();

        assertEquals(CompletionHandler.CompletionStatus.COMPLETED, handler.getStatus());
    }

    @Test
    void testGetStatusReport() {
        CompletionHandler handler = new CompletionHandler();
        handler.verifyTests(true);
        handler.detectEnvironment("normal_repo");

        String report = handler.getStatusReport();
        assertTrue(report.contains("COMPLETION STATUS"));
        assertTrue(report.contains("Tests Pass: YES"));
        assertTrue(report.contains("Environment: normal_repo"));
    }
}
