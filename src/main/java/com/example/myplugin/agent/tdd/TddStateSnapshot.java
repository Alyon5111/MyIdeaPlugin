package com.example.myplugin.agent.tdd;

import java.util.List;

/**
 * Serialized state of a {@link TddEnforcerTool} cycle. A single file in the project
 * persists the enforcer across IDE restarts, so an in-flight RED/GREEN/REFACTOR cycle
 * that ended at a verified state can be resumed instead of being thrown away.
 */
public class TddStateSnapshot {

    private String state;
    private int cycleCount;
    private String currentTask;
    private String linkedPipelineId;
    private List<String> history;
    private String lastTestOutput;
    private String lastTestFailureReason;
    private String savedAt;

    public TddStateSnapshot() {
    }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public int getCycleCount() { return cycleCount; }
    public void setCycleCount(int cycleCount) { this.cycleCount = cycleCount; }

    public String getCurrentTask() { return currentTask; }
    public void setCurrentTask(String currentTask) { this.currentTask = currentTask; }

    public String getLinkedPipelineId() { return linkedPipelineId; }
    public void setLinkedPipelineId(String linkedPipelineId) { this.linkedPipelineId = linkedPipelineId; }

    public List<String> getHistory() { return history; }
    public void setHistory(List<String> history) { this.history = history; }

    public String getLastTestOutput() { return lastTestOutput; }
    public void setLastTestOutput(String lastTestOutput) { this.lastTestOutput = lastTestOutput; }

    public String getLastTestFailureReason() { return lastTestFailureReason; }
    public void setLastTestFailureReason(String lastTestFailureReason) { this.lastTestFailureReason = lastTestFailureReason; }

    public String getSavedAt() { return savedAt; }
    public void setSavedAt(String savedAt) { this.savedAt = savedAt; }
}