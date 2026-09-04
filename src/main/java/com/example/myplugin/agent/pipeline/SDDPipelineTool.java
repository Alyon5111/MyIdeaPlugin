package com.example.myplugin.agent.pipeline;

import com.example.myplugin.agent.AgentTool;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

public class SDDPipelineTool implements AgentTool {

    private final PipelineService pipeline;

    public SDDPipelineTool(PipelineService pipeline) {
        this.pipeline = pipeline;
    }

    @Override
    public String name() {
        return "sdd_pipeline";
    }

    @Override
    public String description() {
        return "Drive the Spec-Driven Development pipeline: SPEC -> PLAN -> TDD -> Change -> DONE. " +
                "Each plan step is executed as one TDD cycle (RED-GREEN-REFACTOR). Change is created at the end.";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
            .name(name())
            .description(description())
            .parameters(JsonObjectSchema.builder()
                .addProperty("action", JsonStringSchema.builder()
                    .description("Action: start, add_step, advance_plan, start_tdd, complete_tdd, link_change, complete, status, list, next_step")
                    .build())
                .addProperty("spec_domain", JsonStringSchema.builder()
                    .description("OpenSpec domain name (for start)")
                    .build())
                .addProperty("requirement", JsonStringSchema.builder()
                    .description("The requirement text flowing through the pipeline (for start)")
                    .build())
                .addProperty("pipeline_id", JsonStringSchema.builder()
                    .description("Pipeline instance id (for most actions)")
                    .build())
                .addProperty("plan_file", JsonStringSchema.builder()
                    .description("Plan file name in docs/plans (for advance_plan)")
                    .build())
                .addProperty("plan_steps", JsonStringSchema.builder()
                    .description("Line-separated implementation steps (for add_step)")
                    .build())
                .addProperty("step_index", JsonStringSchema.builder()
                    .description("Plan step index 0-based (for start_tdd/complete_tdd)")
                    .build())
                .addProperty("change_name", JsonStringSchema.builder()
                    .description("Change name in openspec/changes (for link_change)")
                    .build())
                .build())
            .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        try {
            String action = arguments.has("action") ? arguments.get("action").getAsString() : "list";
            switch (action.toLowerCase()) {
                case "start":
                    return start(arguments);
                case "add_step":
                    return addStep(arguments);
                case "advance_plan":
                    return advancePlan(arguments);
                case "start_tdd":
                    return startTdd(arguments);
                case "complete_tdd":
                    return completeTdd(arguments);
                case "link_change":
                    return linkChange(arguments);
                case "complete":
                    return complete(arguments);
                case "status":
                    return status(arguments);
                case "list":
                    return pipeline.formatSummary();
                case "next_step":
                    return nextStep(arguments);
                default:
                    return "Unknown action: " + action + ". Use: start, add_step, advance_plan, start_tdd, complete_tdd, link_change, complete, status, list, next_step";
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String start(JsonObject arguments) {
        String requirement = arguments.has("requirement") ? arguments.get("requirement").getAsString() : "";
        if (requirement.isEmpty()) {
            return "Error: 'requirement' is required to start a pipeline";
        }
        String specDomain = arguments.has("spec_domain") ? arguments.get("spec_domain").getAsString() : "";

        SpecPipelineInstance instance = pipeline.create(specDomain, requirement);

        StringBuilder sb = new StringBuilder();
        sb.append("=== SPEC PIPELINE STARTED ===\n");
        sb.append("Pipeline ID: ").append(instance.getId()).append("\n");
        sb.append("Requirement: ").append(requirement).append("\n");
        sb.append("Stage: SPEC\n\n");
        sb.append("Keep this pipeline_id to advance it through PLAN -> TDD -> Change.");
        return sb.toString();
    }

    private String addStep(JsonObject arguments) {
        String id = requireId(arguments);
        if (id == null) return "Error: 'pipeline_id' is required";
        String stepsStr = arguments.has("plan_steps") ? arguments.get("plan_steps").getAsString() : "";
        if (stepsStr.isEmpty()) return "Error: 'plan_steps' is required (line-separated steps)";

        String[] steps = stepsStr.split("[\\r\\n]+");
        int added = 0;
        for (String step : steps) {
            String trimmed = step.trim();
            if (trimmed.isEmpty()) continue;
            pipeline.addPlanStep(id, trimmed);
            added++;
        }

        return "Added " + added + " plan step(s) to pipeline " + id + ".\n" +
                "Next: call advance_plan when the full plan is ready.";
    }

    private String advancePlan(JsonObject arguments) {
        String id = requireId(arguments);
        if (id == null) return "Error: 'pipeline_id' is required";
        String planFile = arguments.has("plan_file") ? arguments.get("plan_file").getAsString() : "";

        SpecPipelineInstance instance = pipeline.getById(id);
        if (instance == null) return "Pipeline instance not found: " + id;
        if (instance.getCurrentStage() != PipelineStage.SPEC) {
            return "Can only advance to PLAN from SPEC. Current: " + instance.getCurrentStage();
        }
        if (instance.getPlanSteps().isEmpty()) {
            return "Add plan steps first (action=add_step) or you can pass plan_steps with advance_plan.";
        }

        pipeline.linkPlan(id, planFile);
        pipeline.advance(id, PipelineStage.PLAN, planFile.isEmpty() ? "plan ready" : planFile);

        return "=== STAGE: PLAN ===\n" +
                "Pipeline: " + id + "\n" +
                "Plan file: " + (planFile.isEmpty() ? "(none)" : planFile) + "\n" +
                "Steps: " + instance.getPlanSteps().size() + "\n\n" +
                "Use 'generate_plan' to persist the plan if not already done.\n" +
                "Next: call start_tdd with step_index=0 for the first step.";
    }

    private String startTdd(JsonObject arguments) {
        String id = requireId(arguments);
        if (id == null) return "Error: 'pipeline_id' is required";
        int stepIndex = arguments.has("step_index") ? arguments.get("step_index").getAsInt() : -1;
        if (stepIndex < 0) return "Error: 'step_index' is required";

        SpecPipelineInstance instance = pipeline.getById(id);
        if (instance == null) return "Pipeline instance not found: " + id;
        if (instance.getCurrentStage() != PipelineStage.PLAN && instance.getCurrentStage() != PipelineStage.TDD) {
            return "Can only start TDD from PLAN/TDD. Current: " + instance.getCurrentStage();
        }
        if (instance.getPlanSteps().isEmpty()) {
            return "No plan steps. Call add_step / advance_plan first.";
        }
        if (!pipeline.startStepTdd(id, stepIndex)) {
            return "Invalid step_index " + stepIndex + " (plan has " + instance.getPlanSteps().size() + " steps).";
        }

        if (instance.getCurrentStage() == PipelineStage.PLAN) {
            pipeline.advance(id, PipelineStage.TDD, "starting step " + (stepIndex + 1));
        }

        return "=== STAGE: TDD (step " + (stepIndex + 1) + "/" + instance.getPlanSteps().size() + ") ===\n" +
                "Task: " + instance.getPlanSteps().get(stepIndex) + "\n\n" +
                "Use 'tdd_enforcer start_cycle' with this task, run RED -> GREEN -> REFACTOR.\n" +
                "When the TDD cycle reaches DONE, call pipeline.complete_tdd with step_index=" + stepIndex + ".";
    }

    private String completeTdd(JsonObject arguments) {
        String id = requireId(arguments);
        if (id == null) return "Error: 'pipeline_id' is required";
        int stepIndex = arguments.has("step_index") ? arguments.get("step_index").getAsInt() : -1;
        if (stepIndex < 0) return "Error: 'step_index' is required";

        SpecPipelineInstance instance = pipeline.getById(id);
        if (instance == null) return "Pipeline instance not found: " + id;
        if (!pipeline.completeStepTdd(id, stepIndex)) {
            return "Cannot complete step " + stepIndex + " (not the current step, or invalid index).";
        }

        if (instance.allStepsDone()) {
            pipeline.advance(id, PipelineStage.CHANGE, "all plan steps implemented via TDD");
            return "=== ALL PLAN STEPS DONE ===\n" +
                    "Pipeline advanced to CHANGE.\n" +
                    "Next: create_change + implement + archive, then pipeline.link_change.";
        }

        int nextStep = instance.getCurrentStepIndex() + 1;
        return "=== TDD DONE for step " + (stepIndex + 1) + " ===\n" +
                "Next: call start_tdd with step_index=" + nextStep + " for the next step.";
    }

    private String linkChange(JsonObject arguments) {
        String id = requireId(arguments);
        if (id == null) return "Error: 'pipeline_id' is required";
        String changeName = arguments.has("change_name") ? arguments.get("change_name").getAsString() : "";
        if (changeName.isEmpty()) return "Error: 'change_name' is required";

        SpecPipelineInstance instance = pipeline.getById(id);
        if (instance == null) return "Pipeline instance not found: " + id;

        pipeline.linkChange(id, changeName);
        if (instance.getCurrentStage() == PipelineStage.CHANGE) {
            pipeline.advance(id, PipelineStage.DONE, changeName);
        }

        return "Linked change '" + changeName + "' to pipeline " + id + ". Stage: " + pipeline.getById(id).getCurrentStage() +
                "\nIf the change is archived/merged, call pipeline.complete or it auto-completes when stage is CHANGE.";
    }

    private String complete(JsonObject arguments) {
        String id = requireId(arguments);
        if (id == null) return "Error: 'pipeline_id' is required";
        SpecPipelineInstance instance = pipeline.getById(id);
        if (instance == null) return "Pipeline instance not found: " + id;

        pipeline.advance(id, PipelineStage.DONE, "requirement fully delivered");
        return "Pipeline " + id + " marked DONE (requirement delivered through spec->plan->tdd->change).";
    }

    private String status(JsonObject arguments) {
        String id = requireId(arguments);
        if (id == null) return "Error: 'pipeline_id' is required";
        return pipeline.formatDetail(id);
    }

    private String nextStep(JsonObject arguments) {
        String id = requireId(arguments);
        if (id == null) return "Error: 'pipeline_id' is required";
        SpecPipelineInstance instance = pipeline.getById(id);
        if (instance == null) return "Pipeline instance not found: " + id;

        switch (instance.getCurrentStage()) {
            case SPEC:
                return "Stage: SPEC. Next: add plan steps (action=add_step), then call advance_plan.";
            case PLAN:
                return "Stage: PLAN. Next: call start_tdd with step_index=0 and run the first TDD cycle.";
            case TDD:
                String task = instance.currentStepTddTask();
                return "Stage: TDD (step " + (instance.getCurrentStepIndex() + 1) + "). Next: run tdd_enforcer cycle for '" +
                        (task != null ? task : "current step") + "', then call complete_tdd.";
            case CHANGE:
                return "Stage: CHANGE. Next: create_change + implement + archive, then call link_change to finalize.";
            case DONE:
                return "Stage: DONE. This requirement is complete.";
            default:
                return "Unknown stage: " + instance.getCurrentStage();
        }
    }

    private String requireId(JsonObject arguments) {
        return arguments.has("pipeline_id") ? arguments.get("pipeline_id").getAsString() : null;
    }
}
