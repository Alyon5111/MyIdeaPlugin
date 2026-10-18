package com.example.myplugin.agent.tdd;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentTool;
import com.example.myplugin.agent.pipeline.PipelineService;
import com.example.myplugin.agent.pipeline.PipelineStage;
import com.example.myplugin.agent.pipeline.SpecPipelineInstance;
import com.google.gson.JsonObject;
import com.intellij.openapi.project.Project;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

public class TddEnforcerTool implements AgentTool {

    private final AgentContext context;
    private final TddStateMachine stateMachine = new TddStateMachine();
    private final RedFlagDetector redFlagDetector = new RedFlagDetector();
    private final Path stateFile;
    private PipelineService pipeline;
    private String linkedPipelineId;

    public TddEnforcerTool(AgentContext context) {
        this(context, null);
    }

    public TddEnforcerTool(AgentContext context, PipelineService pipeline) {
        this(context, pipeline, null);
    }

    /** Package-private: lets tests inject a snapshot file location without an IntelliJ Project. */
    TddEnforcerTool(AgentContext context, PipelineService pipeline, Path stateFile) {
        this.context = context;
        this.pipeline = pipeline;
        this.stateFile = stateFile != null ? stateFile : TddEnforcerStorage.defaultPath(project());
        restorePersistedState();
    }

    private Project project() {
        return context != null ? context.getProject() : null;
    }

    private void restorePersistedState() {
        TddStateSnapshot snapshot = TddEnforcerStorage.load(stateFile);
        if (snapshot == null || snapshot.getState() == null) {
            return;
        }
        try {
            stateMachine.restore(
                    TddStateMachine.State.valueOf(snapshot.getState()),
                    snapshot.getCycleCount(),
                    snapshot.getCurrentTask(),
                    snapshot.getHistory(),
                    snapshot.getLastTestOutput(),
                    snapshot.getLastTestFailureReason());
            this.linkedPipelineId = snapshot.getLinkedPipelineId();
        } catch (IllegalArgumentException ignored) {
            // Unknown state string: treat as no persisted state and start from IDLE.
        }
    }

    private void persistState() {
        TddStateSnapshot snapshot = new TddStateSnapshot();
        snapshot.setState(stateMachine.getCurrentState().name());
        snapshot.setCycleCount(stateMachine.getCycleCount());
        snapshot.setCurrentTask(stateMachine.getCurrentTask());
        snapshot.setLinkedPipelineId(linkedPipelineId);
        snapshot.setHistory(stateMachine.getHistory() != null
                ? new ArrayList<>(stateMachine.getHistory()) : new ArrayList<>());
        snapshot.setLastTestOutput(stateMachine.getLastTestOutput());
        snapshot.setLastTestFailureReason(stateMachine.getLastTestFailureReason());
        TddEnforcerStorage.save(stateFile, snapshot);
    }

    /** Pipeline id currently linked to the active cycle, if any. */
    public String linkedPipelineId() {
        return linkedPipelineId;
    }

    /** Task text of the active cycle, if any; helps the panel detect a cycle tied to a different plan step. */
    public String currentTask() {
        return stateMachine.getCurrentTask();
    }

    /**
     * Informational note for the common loop where the current task is identical to an earlier cycle
     * that stalled because the existing tests already passed during RED (i.e. the behavior is already
     * implemented). Surfaces the "keep verified assertions, ADD one new failing test" policy at the
     * natural reading points (cycle start and get_status) instead of only after a verify_test block.
     */
    private String blockedSameTaskNote() {
        String task = stateMachine.getCurrentTask();
        if (task == null || task.isEmpty()) {
            return "";
        }
        String lastCycleTask = null;
        for (String entry : stateMachine.getHistory()) {
            if (entry.startsWith("CYCLE ")) {
                int start = entry.indexOf("Started task '");
                if (start >= 0) {
                    int end = entry.lastIndexOf('\'');
                    if (end > start) {
                        lastCycleTask = entry.substring(start + "Started task '".length(), end);
                    }
                }
            } else if (entry.contains("Test PASSES - TDD cycle broken!") && task.equals(lastCycleTask)) {
                return "\nNote: an earlier cycle attempted this same task and stalled because the existing tests " +
                    "already passed in RED (see history). This usually means the target behavior is already " +
                    "implemented. If so, KEEP the verified assertions and ADD ONE new failing test for a " +
                    "reasonable behavior the current code does not yet support, confirm it fails, then implement it.";
            }
        }
        return "";
    }

    /**
     * Informational note: when the enforcer is linked to a spec pipeline, flag when the current cycle's
     * task text does not match the pipeline's current plan step task shown by the Execute Step panel.
     * Weak models sometimes copy an unrelated old task text from memory or fixtures; surfacing the
     * expected plan-step text (without blocking) lets them self-correct to the right step.
     */
    private String stepTaskMismatchNote() {
        if (linkedPipelineId == null || pipeline == null) {
            return "";
        }
        SpecPipelineInstance instance = pipeline.getById(linkedPipelineId);
        if (instance == null) {
            return "";
        }
        String stepTask = instance.currentStepTddTask();
        String task = stateMachine.getCurrentTask();
        if (stepTask == null || stepTask.isEmpty() || task == null || task.isEmpty() || task.equals(stepTask)) {
            return "";
        }
        return "\nNOTE: the current cycle's task does NOT match the current plan step of linked pipeline "
                + linkedPipelineId + " (step " + (instance.getCurrentStepIndex() + 1) + "/"
                + instance.getPlanSteps().size() + "):\n" + stepTask
                + "\nIf the cycle task is off-step, cancel and start_cycle with the plan step text above.";
    }

    @Override
    public String name() { return "tdd_enforcer"; }

    /** Current state machine state, so a caller (e.g. the executor's stall report) can report where the cycle is. */
    public String currentStateName() {
        return stateMachine.getCurrentState().name();
    }

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
            String action = arguments.has("action") ? arguments.get("action").getAsString() : inferAction(arguments);

            String result;
            switch (action) {
                case "start_cycle":
                    result = startCycle(arguments);
                    break;
                case "write_test":
                    result = writeTest();
                    break;
                case "verify_test":
                    result = verifyTest(arguments);
                    break;
                case "write_code":
                    result = writeCode();
                    break;
                case "verify_code":
                    result = verifyCode(arguments);
                    break;
                case "mark_refactor":
                    result = markRefactor();
                    break;
                case "get_status":
                    result = getStatus();
                    break;
                case "cancel":
                    result = cancel();
                    break;
                case "check_rationalization":
                    result = checkRationalization(arguments);
                    break;
                case "link_pipeline":
                    result = linkPipeline(arguments);
                    break;
                default:
                    result = "Error: Unknown action '" + action + "'. Valid: start_cycle, write_test, verify_test, write_code, verify_code, mark_refactor, get_status, cancel, check_rationalization";
            }

            if (!"get_status".equals(action) && !"check_rationalization".equals(action)) {
                persistState();
            }
            return result;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String inferAction(JsonObject arguments) {
        StringBuilder joined = new StringBuilder();
        for (String key : arguments.keySet()) {
            if (arguments.get(key).isJsonPrimitive()) {
                joined.append('\n').append(arguments.get(key).getAsString());
            }
        }
        String text = joined.toString().toLowerCase();

        String[] actions = {
            "link_pipeline",
            "check_rationalization",
            "mark_refactor",
            "start_cycle",
            "verify_test",
            "write_test",
            "write_code",
            "verify_code",
            "cancel"
        };
        String[] patterns = {
            "link\\s*_?pipeline",
            "rationali[sz]",
            "mark\\s*_?refactor|refactor\\s*(done|complete)",
            "start\\s*_?cycle|begin\\s*a?\\s*cycle|new\\s*cycle|start\\s*task|start\\s*a\\s*new",
            "verif",
            "write\\s*_?test|write\\s*a?\\s*failing\\s*test|wrote\\s*a?\\s*failing\\s*test|writing\\s*a?\\s*failing\\s*test|(\\d+)\\s*tests?\\s*(failed|fail)",
            "write\\s*_?code|minimal\\s*code|implement",
            "tests?\\s+passed|build\\s+successful|all\\s+tests?\\s+pass",
            "cancel"
        };

        for (int i = 0; i < actions.length; i++) {
            if (java.util.regex.Pattern.compile(patterns[i]).matcher(text).find()) {
                return actions[i];
            }
        }

        if (arguments.has("pipeline_id")) {
            if (arguments.has("task") && !arguments.get("task").getAsString().trim().isEmpty()) {
                return "start_cycle";
            }
            return "link_pipeline";
        }
        return "get_status";
    }

    private String startCycle(JsonObject arguments) {
        if (!stateMachine.isIdle() && !stateMachine.isDone()) {
            return "Error: A cycle is already in progress (state: " + stateMachine.getCurrentState() + "). "
                    + stateGuidance();
        }

        String task = arguments.has("task") ? arguments.get("task").getAsString() : "Unnamed task";
        stateMachine.startCycle(task);
        redFlagDetector.clear();

        // Optional pipeline linking in the same call: equivalent to link_pipeline. Weak models
        // (qwen-3-27b) tend to pass pipeline_id here instead of calling link_pipeline, so honor it.
        String pipelineId = arguments.has("pipeline_id") ? arguments.get("pipeline_id").getAsString() : null;
        if (pipelineId != null && !pipelineId.isEmpty()) {
            if (pipeline == null || pipeline.getById(pipelineId) == null) {
                return "Error: Pipeline instance not found: " + pipelineId;
            }
            this.linkedPipelineId = pipelineId;
        }

        return "=== TDD Cycle Started ===\n" +
            "Task: " + task + "\n" +
            "Cycle #" + stateMachine.getCycleCount() + "\n\n" +
            "State: RED\n" +
            "Action: Write a failing test that demonstrates the expected behavior.\n" +
            "Remember: The test MUST fail first. If it passes, TDD is broken.\n\n" +
            "Use 'verify_test' with the test output when ready.\n" + stateGuidance() + blockedSameTaskNote() + stepTaskMismatchNote();
    }

    private String stateGuidance() {
        switch (stateMachine.getCurrentState()) {
            case RED:
                return "NEXT ACTION: write the test file, run it, then call action=verify_test with the real output.";
            case RED_VERIFY:
                return "NEXT ACTION: run the test and call action=verify_test with the real output.";
            case GREEN:
                return "NEXT ACTION: call action=write_code, write the minimal implementation, run the tests, then call action=verify_code with the real output.";
            case GREEN_VERIFY:
                return "NEXT ACTION: run the tests and call action=verify_code with the real output.";
            case REFACTOR:
                return "NEXT ACTION: clean up while keeping tests green, then call action=mark_refactor.";
            case DONE:
                return "NEXT ACTION: the cycle is complete; call action=start_cycle for the next task.";
            case IDLE:
                return "NEXT ACTION: call action=start_cycle with a task to begin.";
            case BLOCKED:
                return "NEXT ACTION: call action=cancel to reset, or action=check_rationalization to address the issue.";
            default:
                return "NEXT ACTION: call action=get_status for details, or action=cancel to reset.";
        }
    }

    private String footer() {
        return "\nCurrent state: " + stateMachine.getCurrentState() + "\n" + stateGuidance();
    }

    private String writeTest() {
        if (stateMachine.getCurrentState() != TddStateMachine.State.RED) {
            return "Error: Can only write test in RED state. Current: " + stateMachine.getCurrentState();
        }

        TddStateMachine.TransitionResult result = stateMachine.transition(null);
        return result.getMessage();
    }

    private String verifyTest(JsonObject arguments) {
        String testOutput = arguments.has("test_output") ? arguments.get("test_output").getAsString() : null;
        if (testOutput == null || testOutput.trim().isEmpty()) {
            return "Error: 'test_output' is required for verify_test";
        }

        // A test written while still in RED should not require a separate bookkeeping call:
        // verifying with real output is itself the acknowledgement that the RED phase is done.
        if (stateMachine.getCurrentState() == TddStateMachine.State.RED) {
            stateMachine.transition(null);
        }
        if (stateMachine.getCurrentState() != TddStateMachine.State.RED_VERIFY) {
            if (stateMachine.getCurrentState() == TddStateMachine.State.GREEN) {
                return "Error: verify_test is only legal in RED_VERIFY.\n"
                    + "Current state: GREEN -- the failing test is ALREADY verified, do NOT re-verify it.\n"
                    + "NEXT ACTION: call action=write_code, implement the code, run the tests, then call action=verify_code.";
            }
            return "Error: Can only verify test in RED_VERIFY state. Current: " + stateMachine.getCurrentState()
                    + ". If still in RED after a 'Test has an error' reply, call action=write_test once, "
                    + "then retry verify_test.";
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

        sb.append(footer());
        return sb.toString();
    }

    private String writeCode() {
        if (stateMachine.getCurrentState() != TddStateMachine.State.GREEN) {
            return "Error: Can only write code in GREEN state. Current: " + stateMachine.getCurrentState() + "\n" + stateGuidance();
        }

        TddStateMachine.TransitionResult result = stateMachine.transition(null);
        return result.getMessage() + "\n" + footer();
    }

    private String verifyCode(JsonObject arguments) {
        String testOutput = arguments.has("test_output") ? arguments.get("test_output").getAsString() : null;
        if (testOutput == null || testOutput.trim().isEmpty()) {
            return "Error: 'test_output' is required for verify_code";
        }

        // Same leniency as verify_test: skip the separate write_code acknowledgement when possible.
        if (stateMachine.getCurrentState() == TddStateMachine.State.GREEN) {
            stateMachine.transition(null);
        }
        if (stateMachine.getCurrentState() != TddStateMachine.State.GREEN_VERIFY) {
            return "Error: Can only verify code in GREEN_VERIFY state. Current: " + stateMachine.getCurrentState()
                    + ". If still in GREEN, call action=write_code once, then retry verify_code.";
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

        sb.append("\n\n").append(stateGuidance());

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
                // Only a full RED->GREEN->REFACTOR cycle closes a step. Record evidence so
                // pipeline.complete_tdd cannot fake completion of an unimplemented step.
                pipeline.recordTddEvidence(linkedPipelineId, stepIndex);
                SpecPipelineInstance after = pipeline.getById(linkedPipelineId);
                if (after != null && after.allStepsDone()) {
                    pipeline.advance(linkedPipelineId, PipelineStage.CHANGE, "all steps implemented via TDD");
                    sb.append("Spec pipeline ").append(linkedPipelineId).append(": ALL plan steps done -> CHANGE.\n");
                    sb.append("Next: create_change + implement + archive, then pipeline.link_change.\n\n");
                } else {
                    sb.append("Spec pipeline ").append(linkedPipelineId).append(": step ").append(stepIndex + 1).append(" TDD done.\n");
                    int nextStep = stepIndex + 1;
                    if (after != null && after.getPlanSteps().size() > nextStep) {
                        pipeline.startStepTdd(linkedPipelineId, nextStep);
                        SpecPipelineInstance advanced = pipeline.getById(linkedPipelineId);
                        sb.append("Current step advanced to step ").append(nextStep + 1).append(": ")
                                .append(advanced.getPlanSteps().get(nextStep)).append("\n\n");
                        sb.append("Next: do Execute Step to run this next plan step's TDD cycle (GREEN_VERIFY resume, or start_cycle/start_tdd for a fresh cycle).\n");
                    } else {
                        sb.append("Next: start the next plan step's TDD cycle, or call pipeline.start_tdd.\n");
                    }
                    sb.append("\n");
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

        String note = blockedSameTaskNote();
        if (!note.isEmpty()) {
            sb.append("\n").append(note);
        }

        String mismatchNote = stepTaskMismatchNote();
        if (!mismatchNote.isEmpty()) {
            sb.append("\n").append(mismatchNote);
        }

        if (redFlagDetector.hasRedFlags()) {
            sb.append("\n=== Active Red Flags ===\n");
            sb.append(redFlagDetector.generateReport());
        }

        sb.append("\n\n").append(stateGuidance());

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
