package com.example.myplugin.agent.subagent;

import com.example.myplugin.agent.AgentTool;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.util.Arrays;
import java.util.List;

public class SubagentDrivenDevelopmentTool implements AgentTool {

    private final SubagentDispatcher dispatcher;
    private final ReviewCoordinator reviewCoordinator;

    public SubagentDrivenDevelopmentTool() {
        this.dispatcher = new SubagentDispatcher();
        this.reviewCoordinator = new ReviewCoordinator();
    }

    @Override
    public String name() {
        return "subagent_driven_development";
    }

    @Override
    public String description() {
        return "Execute plan by dispatching subagents per task with two-stage review (spec + code quality)";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
            .name(name())
            .description(description())
            .parameters(JsonObjectSchema.builder()
                .addProperty("action", JsonStringSchema.builder()
                    .description("Action to perform: dispatch_task, start_task, complete_task, submit_spec_review, submit_code_review, get_status")
                    .build())
                .addProperty("task_id", JsonStringSchema.builder()
                    .description("Task ID (for start_task, complete_task)")
                    .build())
                .addProperty("description", JsonStringSchema.builder()
                    .description("Task description (for dispatch_task)")
                    .build())
                .addProperty("context", JsonStringSchema.builder()
                    .description("Context for the subagent (for dispatch_task)")
                    .build())
                .addProperty("result", JsonStringSchema.builder()
                    .description("Task result (for complete_task)")
                    .build())
                .addProperty("issues", JsonStringSchema.builder()
                    .description("Comma-separated list of issues (for submit_spec_review, submit_code_review)")
                    .build())
                .build())
            .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        try {
            String action = arguments.has("action") ? arguments.get("action").getAsString() : "";

            switch (action.toLowerCase()) {
                case "dispatch_task":
                    return dispatchTask(arguments);
                case "start_task":
                    return startTask(arguments);
                case "complete_task":
                    return completeTask(arguments);
                case "submit_spec_review":
                    return submitSpecReview(arguments);
                case "submit_code_review":
                    return submitCodeReview(arguments);
                case "get_status":
                    return getStatus();
                default:
                    return "Unknown action: " + action + ". Use: dispatch_task, start_task, complete_task, submit_spec_review, submit_code_review, get_status";
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String dispatchTask(JsonObject arguments) {
        String description = arguments.has("description") ? arguments.get("description").getAsString() : null;
        String context = arguments.has("context") ? arguments.get("context").getAsString() : "";

        if (description == null || description.isEmpty()) {
            return "Error: 'description' is required";
        }

        SubagentDispatcher.SubagentTask task = dispatcher.dispatchTask(description, context);

        StringBuilder sb = new StringBuilder();
        sb.append("=== SUBAGENT DISPATCHED ===\n");
        sb.append("Task ").append(task.getId()).append(": ").append(task.getDescription()).append("\n");
        sb.append("Context: ").append(context.isEmpty() ? "None provided" : context).append("\n\n");
        sb.append("Next: Use 'start_task' to begin execution.");
        return sb.toString();
    }

    private String startTask(JsonObject arguments) {
        String taskIdStr = arguments.has("task_id") ? arguments.get("task_id").getAsString() : null;
        if (taskIdStr == null) {
            return "Error: 'task_id' is required";
        }

        int taskId;
        try {
            taskId = Integer.parseInt(taskIdStr);
        } catch (NumberFormatException e) {
            return "Invalid task_id: " + taskIdStr;
        }

        SubagentDispatcher.SubagentTask task = dispatcher.getTask(taskId);
        if (task == null) {
            return "Task not found: " + taskId;
        }

        dispatcher.markRunning(taskId);
        reviewCoordinator.startSpecReview(task.getDescription());

        StringBuilder sb = new StringBuilder();
        sb.append("=== SUBAGENT STARTED ===\n");
        sb.append("Task ").append(taskId).append(": ").append(task.getDescription()).append("\n");
        sb.append("Context:\n").append(task.getContext()).append("\n\n");
        sb.append("Instructions:\n");
        sb.append("1. Implement the task following TDD\n");
        sb.append("2. Write tests first, then implementation\n");
        sb.append("3. Self-review before completing\n");
        sb.append("4. Report: DONE, DONE_WITH_CONCERNS, NEEDS_CONTEXT, or BLOCKED");
        return sb.toString();
    }

    private String completeTask(JsonObject arguments) {
        String taskIdStr = arguments.has("task_id") ? arguments.get("task_id").getAsString() : null;
        String result = arguments.has("result") ? arguments.get("result").getAsString() : "";

        if (taskIdStr == null) {
            return "Error: 'task_id' is required";
        }

        int taskId;
        try {
            taskId = Integer.parseInt(taskIdStr);
        } catch (NumberFormatException e) {
            return "Invalid task_id: " + taskIdStr;
        }

        dispatcher.markCompleted(taskId, result);

        StringBuilder sb = new StringBuilder();
        sb.append("=== SUBAGENT COMPLETED ===\n");
        sb.append("Task ").append(taskId).append(" completed.\n");
        sb.append("Result: ").append(result).append("\n\n");
        sb.append(dispatcher.getSummary());
        return sb.toString();
    }

    private String submitSpecReview(JsonObject arguments) {
        String issuesStr = arguments.has("issues") ? arguments.get("issues").getAsString() : "";
        List<String> issues = issuesStr.isEmpty() ? List.of() : Arrays.asList(issuesStr.split(","));

        ReviewCoordinator.ReviewResult result = reviewCoordinator.submitSpecReview(issues);

        StringBuilder sb = new StringBuilder();
        sb.append("=== SPEC REVIEW RESULT ===\n");

        if (result == ReviewCoordinator.ReviewResult.PASS) {
            sb.append("[PASS] Spec compliance verified.\n");
            sb.append("Next: Submit code quality review.");
        } else {
            sb.append("[NEEDS_FIXES] Spec issues found:\n");
            for (String issue : issues) {
                sb.append("- ").append(issue.trim()).append("\n");
            }
            sb.append("\nImplementer must fix these issues before code quality review.");
        }

        return sb.toString();
    }

    private String submitCodeReview(JsonObject arguments) {
        String issuesStr = arguments.has("issues") ? arguments.get("issues").getAsString() : "";
        List<String> issues = issuesStr.isEmpty() ? List.of() : Arrays.asList(issuesStr.split(","));

        ReviewCoordinator.ReviewResult result = reviewCoordinator.submitCodeQualityReview(issues);

        StringBuilder sb = new StringBuilder();
        sb.append("=== CODE QUALITY REVIEW RESULT ===\n");

        if (result == ReviewCoordinator.ReviewResult.PASS) {
            sb.append("[PASS] Code quality approved.\n");
            sb.append("Task fully reviewed and complete.");
        } else {
            sb.append("[NEEDS_FIXES] Quality issues found:\n");
            for (String issue : issues) {
                sb.append("- ").append(issue.trim()).append("\n");
            }
            sb.append("\nImplementer must fix these issues.");
        }

        return sb.toString();
    }

    private String getStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append(dispatcher.getSummary());
        sb.append("\n");
        sb.append(reviewCoordinator.getStatus());
        return sb.toString();
    }
}
