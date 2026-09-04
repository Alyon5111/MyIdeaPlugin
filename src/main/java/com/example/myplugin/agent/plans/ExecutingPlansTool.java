package com.example.myplugin.agent.plans;

import com.example.myplugin.agent.AgentTool;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.util.Arrays;
import java.util.List;

public class ExecutingPlansTool implements AgentTool {

    private final PlanExecutor executor;

    public ExecutingPlansTool() {
        this.executor = new PlanExecutor();
    }

    @Override
    public String name() {
        return "executing_plans";
    }

    @Override
    public String description() {
        return "Execute implementation plans with task tracking and review checkpoints";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
            .name(name())
            .description(description())
            .parameters(JsonObjectSchema.builder()
                .addProperty("action", JsonStringSchema.builder()
                    .description("Action to perform: load_plan, start_task, complete_task, block_task, get_status, cancel")
                    .build())
                .addProperty("plan_name", JsonStringSchema.builder()
                    .description("Name of the plan (required for load_plan)")
                    .build())
                .addProperty("tasks", JsonStringSchema.builder()
                    .description("Comma-separated list of tasks (required for load_plan)")
                    .build())
                .addProperty("notes", JsonStringSchema.builder()
                    .description("Notes for task completion")
                    .build())
                .addProperty("reason", JsonStringSchema.builder()
                    .description("Reason for blocking task")
                    .build())
                .build())
            .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        try {
            String action = arguments.has("action") ? arguments.get("action").getAsString() : "";

            switch (action.toLowerCase()) {
                case "load_plan":
                    return loadPlan(arguments);
                case "start_task":
                    return startTask();
                case "complete_task":
                    return completeTask(arguments);
                case "block_task":
                    return blockTask(arguments);
                case "get_status":
                    return getStatus();
                case "cancel":
                    return cancel();
                default:
                    return "Unknown action: " + action + ". Use: load_plan, start_task, complete_task, block_task, get_status, cancel";
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String loadPlan(JsonObject arguments) {
        String planName = arguments.has("plan_name") ? arguments.get("plan_name").getAsString() : null;
        String tasksStr = arguments.has("tasks") ? arguments.get("tasks").getAsString() : null;

        if (planName == null || planName.isEmpty()) {
            return "Error: 'plan_name' is required";
        }
        if (tasksStr == null || tasksStr.isEmpty()) {
            return "Error: 'tasks' is required (comma-separated list)";
        }

        List<String> tasks = Arrays.asList(tasksStr.split(","));
        executor.loadPlan(planName, tasks);

        StringBuilder sb = new StringBuilder();
        sb.append("=== PLAN LOADED ===\n");
        sb.append("Plan: ").append(planName).append("\n");
        sb.append("Tasks: ").append(tasks.size()).append("\n\n");
        sb.append("Tasks:\n");
        for (int i = 0; i < tasks.size(); i++) {
            sb.append(i + 1).append(". ").append(tasks.get(i).trim()).append("\n");
        }
        sb.append("\nUse 'start_task' to begin execution.");
        return sb.toString();
    }

    private String startTask() {
        if (!executor.isStarted()) {
            return "No plan loaded. Use 'load_plan' first.";
        }

        String result = executor.startNextTask();
        if (result.equals("ALL TASKS COMPLETED")) {
            return "=== ALL TASKS COMPLETED ===\n\n" + executor.getTracker().getSummary();
        }
        return "=== STARTING TASK ===\n" + result;
    }

    private String completeTask(JsonObject arguments) {
        if (!executor.isStarted()) {
            return "No plan loaded. Use 'load_plan' first.";
        }

        String notes = arguments.has("notes") ? arguments.get("notes").getAsString() : "";
        String result = executor.completeCurrentTask(notes);

        StringBuilder sb = new StringBuilder();
        sb.append("=== TASK COMPLETED ===\n");
        sb.append(result).append("\n\n");
        sb.append(executor.getTracker().getSummary());
        return sb.toString();
    }

    private String blockTask(JsonObject arguments) {
        if (!executor.isStarted()) {
            return "No plan loaded. Use 'load_plan' first.";
        }

        String reason = arguments.has("reason") ? arguments.get("reason").getAsString() : "No reason provided";
        String result = executor.blockCurrentTask(reason);

        StringBuilder sb = new StringBuilder();
        sb.append("=== TASK BLOCKED ===\n");
        sb.append(result).append("\n\n");
        sb.append(executor.getTracker().getSummary());
        return sb.toString();
    }

    private String getStatus() {
        if (!executor.isStarted()) {
            return "No plan loaded.";
        }
        return executor.getStatus();
    }

    private String cancel() {
        executor.cancel();
        return "Plan execution cancelled.";
    }
}
