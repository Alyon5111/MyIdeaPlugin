package com.example.myplugin.agent.verification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VerificationGateTest {

    @Test
    void testInitialState() {
        VerificationGate gate = new VerificationGate();
        assertEquals(VerificationGate.Step.IDLE, gate.getCurrentStep());
        assertNull(gate.getClaim());
        assertNull(gate.getCommand());
        assertNull(gate.getOutput());
        assertFalse(gate.isVerified());
    }

    @Test
    void testStartVerification() {
        VerificationGate gate = new VerificationGate();
        gate.startVerification("All tests pass");

        assertEquals(VerificationGate.Step.IDENTIFY, gate.getCurrentStep());
        assertEquals("All tests pass", gate.getClaim());
        assertFalse(gate.isVerified());
    }

    @Test
    void testIdentifyCommand() {
        VerificationGate gate = new VerificationGate();
        gate.startVerification("All tests pass");

        VerificationGate.TransitionResult result = gate.identifyCommand("./gradlew test");
        assertTrue(result.isSuccess());
        assertEquals(VerificationGate.Step.RUN, gate.getCurrentStep());
        assertEquals("./gradlew test", gate.getCommand());
    }

    @Test
    void testRunCommand() {
        VerificationGate gate = new VerificationGate();
        gate.startVerification("All tests pass");
        gate.identifyCommand("./gradlew test");

        VerificationGate.TransitionResult result = gate.runCommand("BUILD SUCCESSFUL in 2s");
        assertTrue(result.isSuccess());
        assertEquals(VerificationGate.Step.READ, gate.getCurrentStep());
        assertEquals("BUILD SUCCESSFUL in 2s", gate.getOutput());
    }

    @Test
    void testReadOutput() {
        VerificationGate gate = new VerificationGate();
        gate.startVerification("All tests pass");
        gate.identifyCommand("./gradlew test");
        gate.runCommand("BUILD SUCCESSFUL in 2s");

        VerificationGate.TransitionResult result = gate.readOutput("Exit code 0, no failures");
        assertTrue(result.isSuccess());
        assertEquals(VerificationGate.Step.VERIFY, gate.getCurrentStep());
    }

    @Test
    void testVerifyResultConfirmed() {
        VerificationGate gate = new VerificationGate();
        gate.startVerification("All tests pass");
        gate.identifyCommand("./gradlew test");
        gate.runCommand("BUILD SUCCESSFUL in 2s");
        gate.readOutput("Exit code 0, no failures");

        VerificationGate.TransitionResult result = gate.verifyResult(true);
        assertTrue(result.isSuccess());
        assertEquals(VerificationGate.Step.CLAIM, gate.getCurrentStep());
        assertTrue(gate.isVerified());
    }

    @Test
    void testVerifyResultFailed() {
        VerificationGate gate = new VerificationGate();
        gate.startVerification("All tests pass");
        gate.identifyCommand("./gradlew test");
        gate.runCommand("BUILD FAILED");
        gate.readOutput("Exit code 1, 3 failures");

        VerificationGate.TransitionResult result = gate.verifyResult(false);
        assertFalse(result.isSuccess());
        assertEquals(VerificationGate.Step.IDENTIFY, gate.getCurrentStep());
        assertFalse(gate.isVerified());
    }

    @Test
    void testMakeClaimWithoutVerification() {
        VerificationGate gate = new VerificationGate();
        gate.startVerification("All tests pass");

        VerificationGate.TransitionResult result = gate.makeClaim("Done!");
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("without verification"));
    }

    @Test
    void testMakeClaimWithVerification() {
        VerificationGate gate = new VerificationGate();
        gate.startVerification("All tests pass");
        gate.identifyCommand("./gradlew test");
        gate.runCommand("BUILD SUCCESSFUL in 2s");
        gate.readOutput("Exit code 0, no failures");
        gate.verifyResult(true);

        VerificationGate.TransitionResult result = gate.makeClaim("Done!");
        assertTrue(result.isSuccess());
        assertEquals(VerificationGate.Step.DONE, gate.getCurrentStep());
        assertTrue(gate.isDone());
    }

    @Test
    void testCancel() {
        VerificationGate gate = new VerificationGate();
        gate.startVerification("All tests pass");
        gate.identifyCommand("./gradlew test");

        gate.cancel();
        assertEquals(VerificationGate.Step.IDLE, gate.getCurrentStep());
        assertNull(gate.getClaim());
        assertTrue(gate.isIdle());
    }
}
