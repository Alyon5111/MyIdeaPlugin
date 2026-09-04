package com.example.myplugin.agent.plan;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentTool;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class GeneratePlanTool implements AgentTool {

    private final AgentContext context;
    private final PlanGenerator generator = new PlanGenerator();
    private final PlanValidator validator = new PlanValidator();
    private PlanStore planStore;

    public GeneratePlanTool(AgentContext context) {
        this.context = context;
    }

    @Override
    public String name() { return "generate_plan"; }

    @Override
    public String description() {
        return "Generate an implementation plan from a design document or description. Creates a detailed step-by-step plan with TDD workflow.";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
            .name(name())
            .description(description())
            .parameters(JsonObjectSchema.builder()
                .addProperty("action", JsonStringSchema.builder()
                    .description("Action: generate, list, validate, get_status, update_step")
                    .build())
                .addProperty("source", JsonStringSchema.builder()
                    .description("Path to design doc or text description (required for generate)")
                    .build())
                .addProperty("plan_file", JsonStringSchema.builder()
                    .description("Plan file name (for list/get_status/update_step)")
                    .build())
                .addProperty("task_index", dev.langchain4j.model.chat.request.json.JsonIntegerSchema.builder()
                    .description("Task index 0-based (for update_step)")
                    .build())
                .addProperty("step_index", dev.langchain4j.model.chat.request.json.JsonIntegerSchema.builder()
                    .description("Step index 0-based (for update_step)")
                    .build())
                .addProperty("completed", dev.langchain4j.model.chat.request.json.JsonBooleanSchema.builder()
                    .description("Mark step as completed (for update_step)")
                    .build())
                .build())
            .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        try {
            if (planStore == null) {
                planStore = new PlanStore(context.getProject().getBasePath());
            }

            String action = arguments.has("action") ? arguments.get("action").getAsString() : "generate";

            switch (action) {
                case "generate":
                    return generatePlan(arguments);
                case "list":
                    return listPlans();
                case "validate":
                    return validatePlan(arguments);
                case "get_status":
                    return getPlanStatus(arguments);
                case "update_step":
                    return updateStep(arguments);
                default:
                    return "Error: Unknown action '" + action + "'. Valid: generate, list, validate, get_status, update_step";
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String generatePlan(JsonObject arguments) throws Exception {
        String source = arguments.has("source") ? arguments.get("source").getAsString() : null;
        if (source == null || source.trim().isEmpty()) {
            return "Error: 'source' is required for generate action";
        }

        String planContent;
        String featureName;

        if (Files.exists(Paths.get(source))) {
            String designDoc = Files.readString(Paths.get(source));
            planContent = generator.generateFromDesignDoc(designDoc, context.getProject().getBasePath());
            featureName = extractFeatureName(source);
        } else {
            planContent = generator.generateFromDescription(source, context.getProject().getBasePath());
            featureName = source;
        }

        PlanValidator.ValidationResult validation = validator.validate(planContent);

        String fileName = generator.getPlanFileName(featureName);
        planStore.savePlan(planContent, fileName);

        StringBuilder sb = new StringBuilder();
        sb.append("=== Plan Generated ===\n");
        sb.append("File: docs/plans/").append(fileName).append("\n\n");

        if (!validation.isValid()) {
            sb.append("=== Validation Errors ===\n");
            validation.getErrors().forEach(e -> sb.append("ERROR: ").append(e).append("\n"));
            sb.append("\n");
        }

        if (!validation.getWarnings().isEmpty()) {
            sb.append("=== Validation Warnings ===\n");
            validation.getWarnings().forEach(w -> sb.append("WARN: ").append(w).append("\n"));
            sb.append("\n");
        }

        sb.append("=== Plan Preview ===\n");
        sb.append(truncate(planContent, 2000)).append("\n\n");
        sb.append("Use 'list' to see all plans, 'validate' to check a plan.");

        return sb.toString();
    }

    private String listPlans() throws Exception {
        List<PlanStore.PlanInfo> plans = planStore.listPlans();
        if (plans.isEmpty()) {
            return "No plans found. Use 'generate' to create one.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== Implementation Plans ===\n\n");
        for (PlanStore.PlanInfo plan : plans) {
            sb.append(plan.getName()).append("\n");
            sb.append("  Path: ").append(plan.getPath()).append("\n");
            sb.append("  Progress: ").append(plan.getCompletedTasks()).append("/").append(plan.getTaskCount());
            sb.append(" tasks (").append(plan.getProgress()).append("%)\n\n");
        }
        return sb.toString();
    }

    private String validatePlan(JsonObject arguments) throws Exception {
        String planFile = arguments.has("plan_file") ? arguments.get("plan_file").getAsString() : null;
        if (planFile == null) return "Error: 'plan_file' is required for validate";

        String content = planStore.readPlan(planFile);
        if (content == null) return "Error: Plan '" + planFile + "' not found";

        PlanValidator.ValidationResult result = validator.validate(content);

        StringBuilder sb = new StringBuilder();
        sb.append("=== Plan Validation: ").append(planFile).append(" ===\n\n");

        if (result.isValid()) {
            sb.append("Status: VALID\n\n");
        } else {
            sb.append("Status: INVALID\n\n");
        }

        if (!result.getErrors().isEmpty()) {
            sb.append("Errors:\n");
            result.getErrors().forEach(e -> sb.append("  ERROR: ").append(e).append("\n"));
        }

        if (!result.getWarnings().isEmpty()) {
            sb.append("\nWarnings:\n");
            result.getWarnings().forEach(w -> sb.append("  WARN: ").append(w).append("\n"));
        }

        return sb.toString();
    }

    private String getPlanStatus(JsonObject arguments) throws Exception {
        String planFile = arguments.has("plan_file") ? arguments.get("plan_file").getAsString() : null;
        if (planFile == null) return "Error: 'plan_file' is required";

        String content = planStore.readPlan(planFile);
        if (content == null) return "Error: Plan '" + planFile + "' not found";

        int taskCount = 0;
        int completedSteps = 0;
        int totalSteps = 0;

        for (String line : content.split("\n")) {
            if (line.startsWith("## Task ")) taskCount++;
            if (line.contains("- [x]")) { completedSteps++; totalSteps++; }
            if (line.contains("- [ ]")) totalSteps++;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== Plan Status: ").append(planFile).append(" ===\n\n");
        sb.append("Tasks: ").append(taskCount).append("\n");
        sb.append("Steps: ").append(completedSteps).append("/").append(totalSteps).append(" completed\n");
        sb.append("Progress: ").append(totalSteps > 0 ? (completedSteps * 100 / totalSteps) : 0).append("%\n");

        return sb.toString();
    }

    private String updateStep(JsonObject arguments) throws Exception {
        String planFile = arguments.has("plan_file") ? arguments.get("plan_file").getAsString() : null;
        if (planFile == null) return "Error: 'plan_file' is required";

        int taskIndex = arguments.has("task_index") ? arguments.get("task_index").getAsInt() : -1;
        int stepIndex = arguments.has("step_index") ? arguments.get("step_index").getAsInt() : -1;
        boolean completed = arguments.has("completed") && arguments.get("completed").getAsBoolean();

        if (taskIndex < 0 || stepIndex < 0) return "Error: 'task_index' and 'step_index' are required";

        boolean updated = planStore.updateTaskStatus(planFile, taskIndex, stepIndex, completed);
        if (updated) {
            return "Step updated: Task " + taskIndex + ", Step " + stepIndex + " -> " + (completed ? "DONE" : "TODO");
        } else {
            return "Error: Could not update step. Check task/step indices.";
        }
    }

    private String extractFeatureName(String source) {
        String name = Paths.get(source).getFileName().toString();
        name = name.replace("-design.md", "").replace(".md", "");
        return name;
    }

    private String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
}
