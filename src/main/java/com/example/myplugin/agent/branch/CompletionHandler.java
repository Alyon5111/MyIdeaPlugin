package com.example.myplugin.agent.branch;

import java.util.ArrayList;
import java.util.List;

public class CompletionHandler {

    public enum CompletionStatus {
        IDLE,
        TESTS_VERIFIED,
        ENVIRONMENT_DETECTED,
        OPTIONS_PRESENTED,
        OPTION_SELECTED,
        EXECUTING,
        COMPLETED
    }

    private CompletionStatus status;
    private boolean testsPass;
    private String environment;
    private final List<String> history;

    public CompletionHandler() {
        this.status = CompletionStatus.IDLE;
        this.history = new ArrayList<>();
    }

    public CompletionStatus getStatus() { return status; }
    public boolean isTestsPass() { return testsPass; }
    public String getEnvironment() { return environment; }
    public List<String> getHistory() { return history; }

    public void verifyTests(boolean pass) {
        this.testsPass = pass;
        if (pass) {
            this.status = CompletionStatus.TESTS_VERIFIED;
            history.add("TESTS: Verified passing");
        } else {
            history.add("TESTS: Failed - cannot proceed");
        }
    }

    public void detectEnvironment(String env) {
        this.environment = env;
        this.status = CompletionStatus.ENVIRONMENT_DETECTED;
        history.add("ENVIRONMENT: " + env);
    }

    public void presentOptions() {
        this.status = CompletionStatus.OPTIONS_PRESENTED;
        history.add("OPTIONS: Presented to user");
    }

    public void selectOption(String option) {
        this.status = CompletionStatus.OPTION_SELECTED;
        history.add("OPTION SELECTED: " + option);
    }

    public void startExecution() {
        this.status = CompletionStatus.EXECUTING;
        history.add("EXECUTION: Started");
    }

    public void complete() {
        this.status = CompletionStatus.COMPLETED;
        history.add("COMPLETED: All done");
    }

    public boolean canProceed() {
        return testsPass && status == CompletionStatus.TESTS_VERIFIED;
    }

    public String getStatusReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== COMPLETION STATUS ===\n");
        sb.append("Status: ").append(status).append("\n");
        sb.append("Tests Pass: ").append(testsPass ? "YES" : "NO").append("\n");
        sb.append("Environment: ").append(environment).append("\n");
        return sb.toString();
    }
}
