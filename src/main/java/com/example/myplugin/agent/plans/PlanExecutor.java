package com.example.myplugin.agent.plans;

import java.util.ArrayList;
import java.util.List;

public class PlanExecutor {

    private final TaskTracker tracker;
    private final List<String> history;
    private String planName;
    private boolean started;

    public PlanExecutor() {
        this.tracker = new TaskTracker();
        this.history = new ArrayList<>();
        this.started = false;
    }

    public TaskTracker getTracker() { return tracker; }
    public List<String> getHistory() { return history; }
    public String getPlanName() { return planName; }
    public boolean isStarted() { return started; }

    public void loadPlan(String planName, List<String> tasks) {
        this.planName = planName;
        this.started = true;
        history.add("PLAN LOADED: " + planName + " (" + tasks.size() + " tasks)");

        for (String task : tasks) {
            tracker.addTask(task);
        }
    }

    public String startNextTask() {
        TaskTracker.Task next = tracker.getNextPendingTask();
        if (next == null) {
            if (tracker.allCompleted()) {
                return "ALL TASKS COMPLETED";
            }
            return "No pending tasks. Check for blocked tasks.";
        }

        tracker.markInProgress(next.getId());
        history.add("TASK " + next.getId() + " STARTED: " + next.getDescription());
        return "Task " + next.getId() + ": " + next.getDescription();
    }

    public String completeCurrentTask(String notes) {
        TaskTracker.Task inProgress = tracker.getInProgressTask();
        if (inProgress == null) {
            return "No task in progress.";
        }

        inProgress.setNotes(notes);
        tracker.markCompleted(inProgress.getId());
        history.add("TASK " + inProgress.getId() + " COMPLETED: " + notes);
        return "Task " + inProgress.getId() + " completed.";
    }

    public String blockCurrentTask(String reason) {
        TaskTracker.Task inProgress = tracker.getInProgressTask();
        if (inProgress == null) {
            return "No task in progress.";
        }

        tracker.markBlocked(inProgress.getId(), reason);
        history.add("TASK " + inProgress.getId() + " BLOCKED: " + reason);
        return "Task " + inProgress.getId() + " blocked: " + reason;
    }

    public String getStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== PLAN STATUS ===\n");
        sb.append("Plan: ").append(planName).append("\n");
        sb.append(tracker.getSummary());
        sb.append("\nCurrent task: ");
        TaskTracker.Task inProgress = tracker.getInProgressTask();
        if (inProgress != null) {
            sb.append(inProgress.getId()).append(" - ").append(inProgress.getDescription());
        } else {
            sb.append("None");
        }
        return sb.toString();
    }

    public void cancel() {
        history.add("PLAN CANCELLED");
        started = false;
    }
}
