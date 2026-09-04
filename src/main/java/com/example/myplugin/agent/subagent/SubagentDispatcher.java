package com.example.myplugin.agent.subagent;

import java.util.ArrayList;
import java.util.List;

public class SubagentDispatcher {

    public enum SubagentStatus {
        IDLE,
        DISPATCHED,
        RUNNING,
        COMPLETED,
        BLOCKED,
        NEEDS_CONTEXT
    }

    public static class SubagentTask {
        private final int id;
        private final String description;
        private final String context;
        private SubagentStatus status;
        private String result;
        private String questions;

        public SubagentTask(int id, String description, String context) {
            this.id = id;
            this.description = description;
            this.context = context;
            this.status = SubagentStatus.IDLE;
            this.result = "";
            this.questions = "";
        }

        public int getId() { return id; }
        public String getDescription() { return description; }
        public String getContext() { return context; }
        public SubagentStatus getStatus() { return status; }
        public String getResult() { return result; }
        public String getQuestions() { return questions; }

        public void setStatus(SubagentStatus status) { this.status = status; }
        public void setResult(String result) { this.result = result; }
        public void setQuestions(String questions) { this.questions = questions; }
    }

    private final List<SubagentTask> tasks;
    private final List<String> history;
    private int nextId;

    public SubagentDispatcher() {
        this.tasks = new ArrayList<>();
        this.history = new ArrayList<>();
        this.nextId = 1;
    }

    public List<SubagentTask> getTasks() { return tasks; }
    public List<String> getHistory() { return history; }

    public SubagentTask dispatchTask(String description, String context) {
        SubagentTask task = new SubagentTask(nextId++, description, context);
        task.setStatus(SubagentStatus.DISPATCHED);
        tasks.add(task);
        history.add("DISPATCHED: Task " + task.getId() + " - " + description);
        return task;
    }

    public void markRunning(int taskId) {
        SubagentTask task = getTask(taskId);
        if (task != null) {
            task.setStatus(SubagentStatus.RUNNING);
            history.add("RUNNING: Task " + taskId);
        }
    }

    public void markCompleted(int taskId, String result) {
        SubagentTask task = getTask(taskId);
        if (task != null) {
            task.setStatus(SubagentStatus.COMPLETED);
            task.setResult(result);
            history.add("COMPLETED: Task " + taskId);
        }
    }

    public void markBlocked(int taskId, String reason) {
        SubagentTask task = getTask(taskId);
        if (task != null) {
            task.setStatus(SubagentStatus.BLOCKED);
            task.setResult(reason);
            history.add("BLOCKED: Task " + taskId + " - " + reason);
        }
    }

    public void markNeedsContext(int taskId, String questions) {
        SubagentTask task = getTask(taskId);
        if (task != null) {
            task.setStatus(SubagentStatus.NEEDS_CONTEXT);
            task.setQuestions(questions);
            history.add("NEEDS_CONTEXT: Task " + taskId);
        }
    }

    public SubagentTask getTask(int id) {
        for (SubagentTask task : tasks) {
            if (task.getId() == id) {
                return task;
            }
        }
        return null;
    }

    public SubagentTask getRunningTask() {
        for (SubagentTask task : tasks) {
            if (task.getStatus() == SubagentStatus.RUNNING ||
                task.getStatus() == SubagentStatus.DISPATCHED) {
                return task;
            }
        }
        return null;
    }

    public int getCompletedCount() {
        int count = 0;
        for (SubagentTask task : tasks) {
            if (task.getStatus() == SubagentStatus.COMPLETED) count++;
        }
        return count;
    }

    public boolean allCompleted() {
        for (SubagentTask task : tasks) {
            if (task.getStatus() != SubagentStatus.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SUBAGENT SUMMARY ===\n");
        sb.append("Total tasks: ").append(tasks.size()).append("\n");
        sb.append("Completed: ").append(getCompletedCount()).append("\n");
        sb.append("Progress: ").append(getCompletedCount()).append("/").append(tasks.size()).append("\n");
        return sb.toString();
    }
}
