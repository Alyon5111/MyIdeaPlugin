package com.example.myplugin.agent.plans;

import java.util.ArrayList;
import java.util.List;

public class TaskTracker {

    public enum Status {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        BLOCKED
    }

    public static class Task {
        private final int id;
        private final String description;
        private Status status;
        private String notes;

        public Task(int id, String description) {
            this.id = id;
            this.description = description;
            this.status = Status.PENDING;
            this.notes = "";
        }

        public int getId() { return id; }
        public String getDescription() { return description; }
        public Status getStatus() { return status; }
        public String getNotes() { return notes; }

        public void setStatus(Status status) { this.status = status; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    private final List<Task> tasks;

    public TaskTracker() {
        this.tasks = new ArrayList<>();
    }

    public void addTask(String description) {
        tasks.add(new Task(tasks.size() + 1, description));
    }

    public Task getTask(int id) {
        for (Task task : tasks) {
            if (task.getId() == id) {
                return task;
            }
        }
        return null;
    }

    public List<Task> getTasks() { return tasks; }

    public Task getInProgressTask() {
        for (Task task : tasks) {
            if (task.getStatus() == Status.IN_PROGRESS) {
                return task;
            }
        }
        return null;
    }

    public Task getNextPendingTask() {
        for (Task task : tasks) {
            if (task.getStatus() == Status.PENDING) {
                return task;
            }
        }
        return null;
    }

    public void markInProgress(int taskId) {
        Task task = getTask(taskId);
        if (task != null) {
            task.setStatus(Status.IN_PROGRESS);
        }
    }

    public void markCompleted(int taskId) {
        Task task = getTask(taskId);
        if (task != null) {
            task.setStatus(Status.COMPLETED);
        }
    }

    public void markBlocked(int taskId, String reason) {
        Task task = getTask(taskId);
        if (task != null) {
            task.setStatus(Status.BLOCKED);
            task.setNotes(reason);
        }
    }

    public int getCompletedCount() {
        int count = 0;
        for (Task task : tasks) {
            if (task.getStatus() == Status.COMPLETED) count++;
        }
        return count;
    }

    public int getBlockedCount() {
        int count = 0;
        for (Task task : tasks) {
            if (task.getStatus() == Status.BLOCKED) count++;
        }
        return count;
    }

    public boolean allCompleted() {
        for (Task task : tasks) {
            if (task.getStatus() != Status.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== TASK SUMMARY ===\n");
        sb.append("Total: ").append(tasks.size()).append("\n");
        sb.append("Completed: ").append(getCompletedCount()).append("\n");
        sb.append("Blocked: ").append(getBlockedCount()).append("\n");
        sb.append("Progress: ").append(getCompletedCount()).append("/").append(tasks.size()).append("\n");
        return sb.toString();
    }
}
