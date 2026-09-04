package com.example.myplugin.agent.verification;

import java.util.ArrayList;
import java.util.List;

public class VerificationGate {

    public enum Step {
        IDLE,
        IDENTIFY,
        RUN,
        READ,
        VERIFY,
        CLAIM,
        DONE
    }

    private Step currentStep;
    private String claim;
    private String command;
    private String output;
    private boolean verified;
    private final List<String> history;

    public VerificationGate() {
        this.currentStep = Step.IDLE;
        this.history = new ArrayList<>();
    }

    public Step getCurrentStep() { return currentStep; }
    public String getClaim() { return claim; }
    public String getCommand() { return command; }
    public String getOutput() { return output; }
    public boolean isVerified() { return verified; }
    public List<String> getHistory() { return history; }

    public void startVerification(String claim) {
        this.claim = claim;
        this.currentStep = Step.IDENTIFY;
        this.verified = false;
        history.add("VERIFICATION STARTED: " + claim);
    }

    public TransitionResult identifyCommand(String cmd) {
        if (cmd == null || cmd.trim().isEmpty()) {
            return new TransitionResult(false, "Please provide the verification command.");
        }

        this.command = cmd;
        this.currentStep = Step.RUN;
        history.add("IDENTIFY: Command = " + cmd);
        return new TransitionResult(true, "Command identified. Now RUN it and provide the output.");
    }

    public TransitionResult runCommand(String output) {
        if (output == null || output.trim().isEmpty()) {
            return new TransitionResult(false, "Please provide the command output.");
        }

        this.output = output;
        this.currentStep = Step.READ;
        history.add("RUN: Output received (" + output.length() + " chars)");
        return new TransitionResult(true, "Output received. Now READ and analyze it.");
    }

    public TransitionResult readOutput(String analysis) {
        if (analysis == null || analysis.trim().isEmpty()) {
            return new TransitionResult(false, "Please analyze the output.");
        }

        this.currentStep = Step.VERIFY;
        history.add("READ: " + analysis);
        return new TransitionResult(true, "Analysis provided. Now VERIFY: does it confirm the claim?");
    }

    public TransitionResult verifyResult(boolean confirmed) {
        this.verified = confirmed;
        this.currentStep = Step.CLAIM;

        if (confirmed) {
            history.add("VERIFY: Confirmed [OK]");
            return new TransitionResult(true, "Verification PASSED. You may now claim success.");
        } else {
            history.add("VERIFY: FAILED [FAIL]");
            this.currentStep = Step.IDENTIFY;
            return new TransitionResult(false, "Verification FAILED. Do NOT claim success. Investigate further.");
        }
    }

    public TransitionResult makeClaim(String claimText) {
        if (!verified) {
            return new TransitionResult(false, "Cannot claim success without verification. Complete the gate first.");
        }

        this.currentStep = Step.DONE;
        history.add("CLAIM: " + claimText);
        return new TransitionResult(true, "Claim made with verification evidence.");
    }

    public void reset() {
        this.currentStep = Step.IDENTIFY;
        this.claim = null;
        this.command = null;
        this.output = null;
        this.verified = false;
    }

    public void cancel() {
        this.currentStep = Step.IDLE;
        this.claim = null;
        this.command = null;
        this.output = null;
        this.verified = false;
        history.add("CANCELLED");
    }

    public boolean isDone() { return currentStep == Step.DONE; }
    public boolean isIdle() { return currentStep == Step.IDLE; }

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
