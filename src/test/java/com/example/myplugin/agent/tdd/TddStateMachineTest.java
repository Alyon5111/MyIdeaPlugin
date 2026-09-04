package com.example.myplugin.agent.tdd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TddStateMachineTest {

    private TddStateMachine machine;

    @BeforeEach
    void setUp() {
        machine = new TddStateMachine();
    }

    @Test
    void initialState() {
        assertTrue(machine.isIdle());
        assertEquals(TddStateMachine.State.IDLE, machine.getCurrentState());
        assertEquals(0, machine.getCycleCount());
    }

    @Test
    void startCycle() {
        machine.startCycle("Implement login");

        assertEquals(TddStateMachine.State.RED, machine.getCurrentState());
        assertEquals("Implement login", machine.getCurrentTask());
        assertEquals(1, machine.getCycleCount());
    }

    @Test
    void transition_RED_to_RED_VERIFY() {
        machine.startCycle("Test");
        TddStateMachine.TransitionResult result = machine.transition(null);

        assertTrue(result.isSuccess());
        assertEquals(TddStateMachine.State.RED_VERIFY, machine.getCurrentState());
    }

    @Test
    void verifyTest_failsAsExpected() {
        machine.startCycle("Test");
        machine.transition(null); // RED -> RED_VERIFY

        TddStateMachine.TransitionResult result = machine.transition(
            "> Task :test FAILED\nBUILD FAILED\nTestLogin: Expected true but was false");

        assertTrue(result.isSuccess());
        assertEquals(TddStateMachine.State.GREEN, machine.getCurrentState());
    }

    @Test
    void verifyTest_passes_tddBroken() {
        machine.startCycle("Test");
        machine.transition(null); // RED -> RED_VERIFY

        TddStateMachine.TransitionResult result = machine.transition("BUILD SUCCESSFUL\n1 test passed");

        assertFalse(result.isSuccess());
        assertEquals(TddStateMachine.State.BLOCKED, machine.getCurrentState());
        assertTrue(machine.isBlocked());
    }

    @Test
    void verifyTest_emptyOutput() {
        machine.startCycle("Test");
        machine.transition(null); // RED -> RED_VERIFY

        TddStateMachine.TransitionResult result = machine.transition("");

        assertFalse(result.isSuccess());
        assertEquals(TddStateMachine.State.RED_VERIFY, machine.getCurrentState());
    }

    @Test
    void fullCycle_RED_GREEN_REFACTOR_DONE() {
        machine.startCycle("Test");

        // RED -> RED_VERIFY
        machine.transition(null);
        assertEquals(TddStateMachine.State.RED_VERIFY, machine.getCurrentState());

        // RED_VERIFY -> GREEN (test fails)
        machine.transition("BUILD FAILED");
        assertEquals(TddStateMachine.State.GREEN, machine.getCurrentState());

        // GREEN -> GREEN_VERIFY
        machine.transition(null);
        assertEquals(TddStateMachine.State.GREEN_VERIFY, machine.getCurrentState());

        // GREEN_VERIFY -> REFACTOR (tests pass)
        machine.transition("BUILD SUCCESSFUL\n1 test passed");
        assertEquals(TddStateMachine.State.REFACTOR, machine.getCurrentState());

        // REFACTOR -> DONE
        machine.markRefactorDone();
        assertEquals(TddStateMachine.State.DONE, machine.getCurrentState());
        assertTrue(machine.isDone());
    }

    @Test
    void cancel() {
        machine.startCycle("Test");
        machine.cancel();

        assertTrue(machine.isIdle());
        assertNull(machine.getCurrentTask());
    }

    @Test
    void multipleCycles() {
        machine.startCycle("Task 1");
        machine.transition(null);
        machine.transition("BUILD FAILED");
        machine.transition(null);
        machine.transition("BUILD SUCCESSFUL");
        machine.markRefactorDone();

        machine.startCycle("Task 2");

        assertEquals(2, machine.getCycleCount());
        assertEquals("Task 2", machine.getCurrentTask());
    }

    @Test
    void history() {
        machine.startCycle("Test");
        machine.transition(null);
        machine.transition("BUILD FAILED");

        assertFalse(machine.getHistory().isEmpty());
        assertTrue(machine.getHistory().get(0).contains("CYCLE 1"));
    }
}
