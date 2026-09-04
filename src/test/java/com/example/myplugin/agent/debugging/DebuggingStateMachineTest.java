package com.example.myplugin.agent.debugging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DebuggingStateMachineTest {

    @Test
    void testInitialState() {
        DebuggingStateMachine sm = new DebuggingStateMachine();
        assertEquals(DebuggingStateMachine.State.IDLE, sm.getCurrentState());
        assertEquals(0, sm.getAttemptCount());
        assertTrue(sm.getEvidence().isEmpty());
        assertTrue(sm.getAttemptedFixes().isEmpty());
    }

    @Test
    void testStartInvestigation() {
        DebuggingStateMachine sm = new DebuggingStateMachine();
        sm.startInvestigation("NullPointerException in UserService");

        assertEquals(DebuggingStateMachine.State.ROOT_CAUSE, sm.getCurrentState());
        assertEquals("NullPointerException in UserService", sm.getCurrentIssue());
        assertEquals(0, sm.getAttemptCount());
    }

    @Test
    void testTransitionFromIdle() {
        DebuggingStateMachine sm = new DebuggingStateMachine();
        DebuggingStateMachine.TransitionResult result = sm.transition("test");
        assertFalse(result.isSuccess());
        assertEquals("No active investigation. Use 'start_investigation' first.", result.getMessage());
    }

    @Test
    void testRootCauseTransition() {
        DebuggingStateMachine sm = new DebuggingStateMachine();
        sm.startInvestigation("test issue");

        DebuggingStateMachine.TransitionResult result = sm.transition("Error: NullPointerException");
        assertTrue(result.isSuccess());
        assertEquals(DebuggingStateMachine.State.PATTERN, sm.getCurrentState());
        assertEquals(1, sm.getEvidence().size());
    }

    @Test
    void testRootCauseRequiresErrorKeyword() {
        DebuggingStateMachine sm = new DebuggingStateMachine();
        sm.startInvestigation("test issue");

        DebuggingStateMachine.TransitionResult result = sm.transition("something happened");
        assertTrue(result.isSuccess());
        assertEquals(DebuggingStateMachine.State.ROOT_CAUSE, sm.getCurrentState());
    }

    @Test
    void testPatternTransition() {
        DebuggingStateMachine sm = new DebuggingStateMachine();
        sm.startInvestigation("test issue");
        sm.transition("Error: NullPointerException");

        DebuggingStateMachine.TransitionResult result = sm.transition("Similar to UserService which works");
        assertTrue(result.isSuccess());
        assertEquals(DebuggingStateMachine.State.HYPOTHESIS, sm.getCurrentState());
    }

    @Test
    void testHypothesisRequiresBecause() {
        DebuggingStateMachine sm = new DebuggingStateMachine();
        sm.startInvestigation("test issue");
        sm.transition("Error: NullPointerException");
        sm.transition("Similar to UserService");

        DebuggingStateMachine.TransitionResult result = sm.transition("I think it's null");
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("because"));
    }

    @Test
    void testHypothesisWithBecause() {
        DebuggingStateMachine sm = new DebuggingStateMachine();
        sm.startInvestigation("test issue");
        sm.transition("Error: NullPointerException");
        sm.transition("Similar to UserService");

        DebuggingStateMachine.TransitionResult result = sm.transition("I think it's null because user isn't checked");
        assertTrue(result.isSuccess());
        assertEquals(DebuggingStateMachine.State.IMPLEMENTATION, sm.getCurrentState());
        assertEquals("I think it's null because user isn't checked", sm.getHypothesis());
    }

    @Test
    void testImplementationSuccess() {
        DebuggingStateMachine sm = new DebuggingStateMachine();
        sm.startInvestigation("test issue");
        sm.transition("Error: NullPointerException");
        sm.transition("Similar to UserService");
        sm.transition("I think it's null because user isn't checked");

        DebuggingStateMachine.TransitionResult result = sm.transition("Fixed! Tests pass now.");
        assertTrue(result.isSuccess());
        assertEquals(DebuggingStateMachine.State.DONE, sm.getCurrentState());
        assertEquals(1, sm.getAttemptCount());
    }

    @Test
    void testImplementationFailure() {
        DebuggingStateMachine sm = new DebuggingStateMachine();
        sm.startInvestigation("test issue");
        sm.transition("Error: NullPointerException");
        sm.transition("Similar to UserService");
        sm.transition("I think it's null because user isn't checked");

        DebuggingStateMachine.TransitionResult result = sm.transition("Still fails with error");
        assertFalse(result.isSuccess());
        assertEquals(DebuggingStateMachine.State.ROOT_CAUSE, sm.getCurrentState());
        assertEquals(1, sm.getAttemptCount());
    }

    @Test
    void testBlockedAfterThreeFailures() {
        DebuggingStateMachine sm = new DebuggingStateMachine();
        sm.startInvestigation("test issue");
        sm.transition("Error: NullPointerException");
        sm.transition("Similar to UserService");
        sm.transition("I think it's null because user isn't checked");
        sm.transition("Still fails 1");
        sm.transition("Error: NullPointerException");
        sm.transition("Similar to UserService");
        sm.transition("I think it's null because user isn't checked");
        sm.transition("Still fails 2");
        sm.transition("Error: NullPointerException");
        sm.transition("Similar to UserService");
        sm.transition("I think it's null because user isn't checked");

        DebuggingStateMachine.TransitionResult result = sm.transition("Still fails 3");
        assertFalse(result.isSuccess());
        assertEquals(DebuggingStateMachine.State.BLOCKED, sm.getCurrentState());
        assertTrue(sm.isBlocked());
    }

    @Test
    void testCancel() {
        DebuggingStateMachine sm = new DebuggingStateMachine();
        sm.startInvestigation("test issue");
        sm.transition("Error: NullPointerException");

        sm.cancel();
        assertEquals(DebuggingStateMachine.State.IDLE, sm.getCurrentState());
        assertNull(sm.getCurrentIssue());
        assertTrue(sm.isIdle());
    }
}
