package com.example.myplugin.agent.pipeline;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentTool;
import com.example.myplugin.agent.plan.PlanGenerator;
import com.example.myplugin.agent.plan.PlanStore;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public class SDDPipelineTool implements AgentTool {

    private static final String SPECS_REL = "openspec/specs";

    private final PipelineService pipeline;
    private final Path baseDir;

    public SDDPipelineTool(PipelineService pipeline) {
        this(pipeline, null);
    }

    public SDDPipelineTool(AgentContext context, PipelineService pipeline) {
        this(pipeline, context != null ? context.getBaseDir() : null);
    }

    SDDPipelineTool(PipelineService pipeline, Path baseDir) {
        this.pipeline = pipeline;
        this.baseDir = baseDir;
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
                    .description("The requirement text flowing through the pipeline. Write it in English; this becomes the body of the generated spec doc (for start)")
                    .build())
                .addProperty("note_zh", JsonStringSchema.builder()
                    .description("Optional Chinese remark (备注) attached to the generated spec doc. The spec body stays in English (for start)")
                    .build())
                .addProperty("pipeline_id", JsonStringSchema.builder()
                    .description("Pipeline instance id (for most actions; optional for status - when omitted, status prints every active instance with its ID)")
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

    private String inferAction(JsonObject arguments) {
        if (arguments.has("change_name")) return "link_change";
        if (arguments.has("plan_steps")) return "add_step";
        if (arguments.has("plan_file")) return "advance_plan";
        if (arguments.has("step_index")) {
            String id = arguments.has("pipeline_id") ? arguments.get("pipeline_id").getAsString() : "";
            SpecPipelineInstance instance = pipeline.getById(id);
            int stepIndex = arguments.get("step_index").getAsInt();
            if (instance != null && instance.getCurrentStage() == PipelineStage.TDD
                    && instance.getCurrentStepIndex() == stepIndex && !instance.allStepsDone()) {
                return "complete_tdd";
            }
            return "start_tdd";
        }
        if (arguments.has("requirement")) return "start";
        if (arguments.has("pipeline_id")) return "status";
        return "list";
    }

    @Override
    public String execute(JsonObject arguments) {
        try {
            String action = arguments.has("action") ? arguments.get("action").getAsString() : "";
            if (action.isEmpty()) {
                action = inferAction(arguments);
            }
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
        String noteZh = arguments.has("note_zh") ? arguments.get("note_zh").getAsString() : "";

        SpecPipelineInstance instance = pipeline.create(specDomain, requirement);

        StringBuilder sb = new StringBuilder();
        sb.append("=== SPEC PIPELINE STARTED ===\n");
        sb.append("Pipeline ID: ").append(instance.getId()).append("\n");
        sb.append("Requirement: ").append(requirement).append("\n");
        sb.append("Stage: SPEC\n\n");

        String specDoc = writeSpecDoc(specDomain, requirement, noteZh);
        if (specDoc != null) {
            sb.append("Spec doc: ").append(specDoc).append("\n");
        }
        if (specDomain.isEmpty()) {
            sb.append("Note: no spec_domain given, no spec doc generated.\n");
        }

        sb.append("Keep this pipeline_id to advance it through PLAN -> TDD -> Change.");
        return sb.toString();
    }

    private String writeSpecDoc(String specDomain, String requirement, String noteZh) {
        if (baseDir == null || specDomain.trim().isEmpty()) {
            return null;
        }
        try {
            Path specDir = baseDir.resolve(SPECS_REL).resolve(specDomain.trim());
            Path specFile = specDir.resolve("spec.md");
            if (Files.exists(specFile)) {
                return SPECS_REL + "/" + specDomain.trim() + "/spec.md (already exists)";
            }
            Files.createDirectories(specDir);
            Files.writeString(specFile, buildSpecContent(specDomain.trim(), requirement, noteZh), StandardCharsets.UTF_8);
            return SPECS_REL + "/" + specDomain.trim() + "/spec.md";
        } catch (Exception e) {
            return "Error writing spec doc: " + e.getMessage();
        }
    }

    static String buildSpecContent(String domain, String requirement, String noteZh) {
        String[] words = domain.split("[^A-Za-z0-9]+");
        StringBuilder title = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            title.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) title.append(w.substring(1).toLowerCase(Locale.ROOT));
            title.append(' ');
        }
        String cleanTitle = title.toString().trim();
        if (cleanTitle.isEmpty()) cleanTitle = domain;

        String req = requirement.trim();
        String reqSentence = req.endsWith(".") ? req : req + ".";

        String body = "# " + cleanTitle + "\n" +
                      "\n" +
                      "## Purpose\n" +
                      "\n" +
                      "Spec-driven development starts here. This spec captures the requirement that flows\n" +
                      "through the pipeline SPEC -> PLAN -> TDD -> Change -> DONE.\n" +
                      "\n" +
                      "## Requirements\n" +
                      "\n" +
                      "### Requirement: " + req + "\n" +
                      "\n" +
                      "The system SHALL " + reqSentence + "\n" +
                      "\n" +
                      "#### Scenario: Satisfy the requirement\n" +
                      "\n" +
                      "- WHEN the pipeline executes the requirement\n" +
                      "- THEN the requirement is implemented and verified via TDD\n";

        if (noteZh != null && !noteZh.trim().isEmpty()) {
            body += "\n> Note (Chinese): " + noteZh.trim() + "\n";
        }
        return body;
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

        if (planFile.isEmpty() && baseDir != null) {
            String generated = generatePlanFile(instance);
            if (generated != null) {
                planFile = generated;
            }
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

    private String generatePlanFile(SpecPipelineInstance instance) {
        try {
            String source = instance.getRequirement();
            PlanGenerator generator = new PlanGenerator();
            String content = generator.generateFromDescription(source, baseDir.toString());
            String slugBase = (instance.getSpecDomain() != null && !instance.getSpecDomain().trim().isEmpty())
                    ? instance.getSpecDomain().trim() : source;
            String fileName = generator.getPlanFileName(slugBase);
            new PlanStore(baseDir.toString()).savePlan(content, fileName);
            return fileName;
        } catch (Exception e) {
            return null;
        }
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
        if (id == null) {
            List<SpecPipelineInstance> active = pipeline.getActive();
            if (active.isEmpty()) {
                return "No active pipeline instances. Use pipeline.start to begin.";
            }
            StringBuilder sb = new StringBuilder();
            for (SpecPipelineInstance i : active) {
                if (sb.length() > 0) sb.append("\n\n");
                sb.append(pipeline.formatDetail(i.getId()));
            }
            return sb.toString();
        }
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
