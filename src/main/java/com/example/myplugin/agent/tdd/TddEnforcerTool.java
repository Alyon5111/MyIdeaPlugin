package com.example.myplugin.agent.tdd;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentTool;
import com.example.myplugin.agent.pipeline.PipelineService;
import com.example.myplugin.agent.pipeline.PipelineStage;
import com.example.myplugin.agent.pipeline.SpecPipelineInstance;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

public class TddEnforcerTool implements AgentTool {

    private final AgentContext context;
    private final TddStateMachine stateMachine = new TddStateMachine();
    private final RedFlagDetector redFlagDetector = new RedFlagDetector();
    private PipelineService pipeline;
    private String linkedPipelineId;

    public TddEnforcerTool(AgentContext context) {
        this.context = context;
    }

    public TddEnforcerTool(AgentContext context, PipelineService pipeline) {
        this.context = context;
        this.pipeline = pipeline;
    }

    @Override
    public String name() { return "tdd_enforcer"; }

    @Override
    public String description() {
        return "Enforce Test-Driven Development workflow. Manages RED-GREEN-REFACTOR cycle and detects TDD violations.";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
            .name(name())
            .description(description())
            .parameters(JsonObjectSchema.builder()
                .addProperty("action", JsonStringSchema.builder()
                    .description("Action: start_cycle, write_test, verify_test, write_code, verify_code, mark_refactor, get_status, cancel, check_rationalization, link_pipeline")
                    .build())
                .addProperty("task", JsonStringSchema.builder()
                    .description("Task description (for start_cycle)")
                    .build())
                .addProperty("test_output", JsonStringSchema.builder()
                    .description("Test run output (for verify_test, verify_code)")
                    .build())
                .addProperty("message", JsonStringSchema.builder()
                    .description("User message to check for rationalization (for check_rationalization)")
                    .build())
                .addProperty("pipeline_id", JsonStringSchema.builder()
                    .description("Spec-driven pipeline id to link (for link_pipeline)")
                    .build())
                .build())
            .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        try {
            String action = arguments.has("action") ? arguments.get("action").getAsString() : "get_status";

            switch (action) {
                case "start_cycle":
                    return startCycle(arguments);
                case "write_test":
                    return writeTest();
                case "verify_test":
                    return verifyTest(arguments);
                case "write_code":
                    return writeCode();
                case "verify_code":
                    return verifyCode(arguments);
                case "mark_refactor":
                    return markRefactor();
                case "get_status":
                    return getStatus();
                case "cancel":
                    return cancel();
                case "check_rationalization":
                    return checkRationalization(arguments);
                case "link_pipeline":
                    return linkPipeline(arguments);
                default:
                    return "Error: Unknown action '" + action + "'. Valid: start_cycle, write_test, verify_test, write_code, verify_code, mark_refactor, get_status, cancel, check_rationalization";
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String startCycle(JsonObject arguments) {
        if (!stateMachine.isIdle() && !stateMachine.isDone()) {
            return "Error: A cycle is already in progress (state: " + stateMachine.getCurrentState() + "). Cancel it first or complete it.";
        }

        String task = arguments.has("task") ? arguments.get("task").getAsString() : "Unnamed task";
        stateMachine.startCycle(task);
        redFlagDetector.clear();

        return "=== TDD Cycle Started ===\n" +
            "Task: " + task + "\n" +
            "Cycle #" + stateMachine.getCycleCount() + "\n\n" +
            "State: RED\n" +
            "Action: Write a failing test that demonstrates the expected behavior.\n" +
            "Remember: The test MUST fail first. If it passes, TDD is broken.\n\n" +
            "Use 'verify_test' with the test output when ready.";
    }

    private String writeTest() {
        if (stateMachine.getCurrentState() != TddStateMachine.State.RED) {
            return "Error: Can only write test in RED state. Current: " + stateMachine.getCurrentState();
        }

        TddStateMachine.TransitionResult result = stateMachine.transition(null);
        return result.getMessage();
    }

    private String verifyTest(JsonObject arguments) {
        if (stateMachine.getCurrentState() != TddStateMachine.State.RED_VERIFY) {
            return "Error: Can only verify test in RED_VERIFY state. Current: " + stateMachine.getCurrentState();
        }

        String testOutput = arguments.has("test_output") ? arguments.get("test_output").getAsString() : null;
        if (testOutput == null || testOutput.trim().isEmpty()) {
            return "Error: 'test_output' is required for verify_test";
        }

        TddStateMachine.TransitionResult result = stateMachine.transition(testOutput);

        StringBuilder sb = new StringBuilder();
        sb.append(result.getMessage()).append("\n\n");

        if (!result.isSuccess()) {
            sb.append("=== Red Flags ===\n");
            redFlagDetector.detectTestImmediatelyPassing(testOutput);
            if (redFlagDetector.hasRedFlags()) {
                sb.append(redFlagDetector.generateReport());
            }
        }

        sb.append("\nCurrent state: ").append(stateMachine.getCurrentState());
        return sb.toString();
    }

    private String writeCode() {
        if (stateMachine.getCurrentState() != TddStateMachine.State.GREEN) {
            return "Error: Can only write code in GREEN state. Current: " + stateMachine.getCurrentState();
        }

        TddStateMachine.TransitionResult result = stateMachine.transition(null);
        return result.getMessage();
    }

    private String verifyCode(JsonObject arguments) {
        if (stateMachine.getCurrentState() != TddStateMachine.State.GREEN_VERIFY) {
            return "Error: Can only verify code in GREEN_VERIFY state. Current: " + stateMachine.getCurrentState();
        }

        String testOutput = arguments.has("test_output") ? arguments.get("test_output").getAsString() : null;
        if (testOutput == null || testOutput.trim().isEmpty()) {
            return "Error: 'test_output' is required for verify_code";
        }

        TddStateMachine.TransitionResult result = stateMachine.transition(testOutput);

        StringBuilder sb = new StringBuilder();
        sb.append(result.getMessage()).append("\n\n");
        sb.append("Current state: ").append(stateMachine.getCurrentState());

        if (stateMachine.getCurrentState() == TddStateMachine.State.REFACTOR) {
            sb.append("\n\n=== Next Steps ===\n");
            sb.append("1. Remove duplicates\n");
            sb.append("2. Improve naming\n");
            sb.append("3. Extract helper methods\n");
            sb.append("4. Run tests after each change\n");
            sb.append("5. Use 'mark_refactor' when done");
        }

        return sb.toString();
    }

    private String markRefactor() {
        if (stateMachine.getCurrentState() != TddStateMachine.State.REFACTOR) {
            return "Error: Can only mark refactor in REFACTOR state. Current: " + stateMachine.getCurrentState();
        }

        stateMachine.markRefactorDone();

        StringBuilder sb = new StringBuilder();
        sb.append("=== TDD Cycle Complete ===\n");
        sb.append("Task: ").append(stateMachine.getCurrentTask()).append("\n");
        sb.append("Cycle #").append(stateMachine.getCycleCount()).append(" completed successfully.\n\n");

        if (linkedPipelineId != null && pipeline != null) {
            SpecPipelineInstance instance = pipeline.getById(linkedPipelineId);
            int stepIndex = instance != null ? instance.getCurrentStepIndex() : -1;
            boolean completed = stepIndex >= 0 && pipeline.completeStepTdd(linkedPipelineId, stepIndex);
            if (completed) {
                SpecPipelineInstance after = pipeline.getById(linkedPipelineId);
                if (after != null && after.allStepsDone()) {
                    pipeline.advance(linkedPipelineId, PipelineStage.CHANGE, "all steps implemented via TDD");
                    sb.append("Spec pipeline ").append(linkedPipelineId).append(": ALL plan steps done -> CHANGE.\n");
                    sb.append("Next: create_change + implement + archive, then pipeline.link_change.\n\n");
                } else {
                    sb.append("Spec pipeline ").append(linkedPipelineId).append(": step ").append(stepIndex + 1).append(" TDD done.\n");
                    sb.append("Next: start the next plan step's TDD cycle, or call pipeline.start_tdd.\n\n");
                }
            } else {
                sb.append("Spec pipeline ").append(linkedPipelineId).append(": linked but no active TDD step. Use pipeline.start_tdd first.\n\n");
            }
        }

        sb.append("Start a new cycle with 'start_cycle' for the next task.");
        return sb.toString();
    }

    private String linkPipeline(JsonObject arguments) {
        String pipelineId = arguments.has("pipeline_id") ? arguments.get("pipeline_id").getAsString() : "";
        if (pipelineId.isEmpty()) {
            return "Error: 'pipeline_id' is required for link_pipeline";
        }
        if (pipeline == null || pipeline.getById(pipelineId) == null) {
            return "Error: Pipeline instance not found: " + pipelineId;
        }
        this.linkedPipelineId = pipelineId;
        return "Linked TDD cycle to spec pipeline " + pipelineId + ".";
    }

    private String getStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== TDD Enforcer Status ===\n\n");
        sb.append("State: ").append(stateMachine.getCurrentState()).append("\n");
        sb.append("Cycles completed: ").append(stateMachine.getCycleCount()).append("\n");

        if (stateMachine.getCurrentTask() != null) {
            sb.append("Current task: ").append(stateMachine.getCurrentTask()).append("\n");
        }

        sb.append("\n=== State Machine ===\n");
        sb.append("IDLE -> RED -> RED_VERIFY -> GREEN -> GREEN_VERIFY -> REFACTOR -> DONE\n");
        sb.append("                        |              |\n");
        sb.append("                        v              v\n");
        sb.append("                     BLOCKED         GREEN (retry)\n\n");

        sb.append("=== History ===\n");
        for (String entry : stateMachine.getHistory()) {
            sb.append("  ").append(entry).append("\n");
        }

        if (redFlagDetector.hasRedFlags()) {
            sb.append("\n=== Active Red Flags ===\n");
            sb.append(redFlagDetector.generateReport());
        }

        return sb.toString();
    }

    private String cancel() {
        stateMachine.cancel();
        redFlagDetector.clear();
        return "TDD cycle cancelled. State reset to IDLE.";
    }

    private String checkRationalization(JsonObject arguments) {
        String message = arguments.has("message") ? arguments.get("message").getAsString() : null;
        if (message == null || message.trim().isEmpty()) {
            return "Error: 'message' is required for check_rationalization";
        }

        redFlagDetector.detectRationalization(message);

        if (redFlagDetector.hasRedFlags()) {
            return "=== Rationalization Detected ===\n\n" +
                redFlagDetector.generateReport() + "\n\n" +
                "Remember: TDD is not optional. The Iron Law states:\n" +
                "NO PRODUCTION CODE WITHOUT A FAILING TEST FIRST.\n\n" +
                "If you think TDD doesn't apply here, that's a rationalization. Stop and write the test.";
        }

        return "No rationalization detected. Continue with TDD workflow.";
    }
}
