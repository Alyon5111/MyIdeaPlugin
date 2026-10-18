package com.example.myplugin.ui;

import com.example.myplugin.agent.pipeline.PipelineService;
import com.example.myplugin.agent.pipeline.PipelineStage;
import com.example.myplugin.agent.pipeline.SpecPipelineInstance;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UI-level tests for {@link PipelinePanel}. The panel is built via its package-private
 * test constructor with an in-memory pipeline service and a fake tool runner, so no
 * IntelliJ Project is required.
 */
class PipelinePanelTest {

    private PipelineService service;
    private FakeToolRunner runner;
    private PipelinePanel panel;

    /** Records tool invocations and returns canned results. */
    private static class FakeToolRunner implements PipelinePanel.ToolRunner {
        final List<String> calls = new ArrayList<>();
        String result = "OK";
        RuntimeException toThrow;

        @Override
        public String run(String toolName, JsonObject args) {
            calls.add(toolName + "|" + args.toString());
            if (toThrow != null) {
                throw toThrow;
            }
            return result;
        }

        String lastCall() {
            return calls.isEmpty() ? null : calls.get(calls.size() - 1);
        }

        JsonObject lastArgs() {
            String last = lastCall();
            if (last == null) {
                return null;
            }
            return JsonParser.parseString(last.substring(last.indexOf('|') + 1)).getAsJsonObject();
        }
    }

    @BeforeEach
    void setUp() {
        service = PipelineService.inMemory();
        runner = new FakeToolRunner();
        panel = new PipelinePanel(service, runner);
    }

    private SpecPipelineInstance createInstance(String domain, String requirement) {
        return service.create(domain, requirement);
    }

    private void refresh() {
        panel.refreshStatus();
    }

    // ---------- empty state ----------

    @Test
    void emptyStateShowsPlaceholderAndDisablesSelectionButtons() {
        assertEquals(0, panel.instanceModel.size());
        assertEquals("No active pipeline instance selected.", panel.summaryLabel.getText());
        assertFalse(panel.advancePlanItem.isEnabled());
        assertFalse(panel.startTddItem.isEnabled());
        assertFalse(panel.executeStepItem.isEnabled());
        assertFalse(panel.completeItem.isEnabled());
        assertFalse(panel.statusItem.isEnabled());
        assertFalse(panel.linkChangeItem.isEnabled());
        // Always available regardless of selection:
        assertTrue(panel.exportPlanItem.isEnabled());
        assertTrue(panel.listItem.isEnabled());
        assertTrue(panel.createChangeItem.isEnabled());
    }

    @Test
    void doneInstancesListedUnderCompletedGroup() {
        createInstance("auth", "active requirement");
        SpecPipelineInstance done = createInstance("billing", "finished requirement");
        service.advance(done.getId(), PipelineStage.DONE, "done");

        refresh();

        assertEquals(2, panel.instanceModel.size());
        assertEquals("[SPEC] active requirement  (auth)", panel.instanceModel.get(0));
        assertEquals("[DONE] finished requirement  (billing)", panel.instanceModel.get(1));

        assertEquals(2, panel.treeRoot.getChildCount(), "active + completed groups expected");

        DefaultMutableTreeNode activeGroup = (DefaultMutableTreeNode) panel.treeRoot.getChildAt(0);
        DefaultMutableTreeNode doneGroup = (DefaultMutableTreeNode) panel.treeRoot.getChildAt(1);
        assertEquals("Active (1)", activeGroup.getUserObject());
        assertEquals("Completed (1)", doneGroup.getUserObject());

        panel.selectFirstInstance();
        SpecPipelineInstance selected = selectedByTree();
        assertNotNull(selected);
        assertEquals("active requirement", selected.getRequirement());
        assertEquals("SPEC", selected.getCurrentStage().name());
    }

    private SpecPipelineInstance selectedByTree() {
        if (panel.instanceTree.isSelectionEmpty()) {
            return null;
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) panel.instanceTree.getLastSelectedPathComponent();
        while (node != null && node != panel.treeRoot) {
            Object user = node.getUserObject();
            if (user instanceof SpecPipelineInstance inst) {
                return inst;
            }
            node = (DefaultMutableTreeNode) node.getParent();
        }
        return null;
    }

    // ---------- list rendering ----------

    @Test
    void instanceLabelShowsStageRequirementAndDomain() {
        createInstance("auth", "Users can log in");
        refresh();

        assertEquals("[SPEC] Users can log in  (auth)", panel.instanceModel.get(0));
    }

    @Test
    void instanceLabelTruncatesLongRequirements() {
        String longReq = "a".repeat(50);
        createInstance("auth", longReq);
        refresh();

        String label = panel.instanceModel.get(0);
        assertTrue(label.contains("..."), "expected truncated label, got: " + label);
        assertEquals("[SPEC] " + longReq.substring(0, 30) + "...  (auth)", label);
    }

    @Test
    void instanceLabelFallsBackToNoSpecWhenDomainMissing() {
        createInstance("", "no domain requirement");
        refresh();

        assertEquals("[SPEC] no domain requirement  (no-spec)", panel.instanceModel.get(0));
    }

    @Test
    void selectionShowsStepSummary() {
        SpecPipelineInstance inst = createInstance("auth", "requirement for summary");
        service.advance(inst.getId(), PipelineStage.PLAN, "planned");
        service.addPlanStep(inst.getId(), "step one");
        service.addPlanStep(inst.getId(), "step two");
        service.startStepTdd(inst.getId(), 0);
        refresh();

        panel.selectFirstInstance();
        assertTrue(panel.summaryLabel.getText().startsWith("[PLAN] step 1/2"));
        assertTrue(panel.summaryLabel.getText().contains("requirement for summary"));
    }

    // ---------- button enablement per stage ----------

    private void selectFirst() {
        panel.selectFirstInstance();
    }

    @Test
    void stageSpecEnablesOnlyAdvancePlan() {
        createInstance("auth", "req");
        refresh();
        selectFirst();

        assertTrue(panel.advancePlanItem.isEnabled());
        assertFalse(panel.startTddItem.isEnabled());
        assertFalse(panel.executeStepItem.isEnabled());
        assertFalse(panel.completeItem.isEnabled());
        assertTrue(panel.statusItem.isEnabled());
    }

    @Test
    void stagePlanEnablesStartTddOnly() {
        SpecPipelineInstance inst = createInstance("auth", "req");
        service.advance(inst.getId(), PipelineStage.PLAN, "planned");
        refresh();
        selectFirst();

// Advance Plan is SPEC->PLAN, so it is disabled once the instance is in PLAN.
        assertFalse(panel.advancePlanItem.isEnabled());
        assertTrue(panel.startTddItem.isEnabled());
        assertFalse(panel.executeStepItem.isEnabled());
        assertFalse(panel.completeItem.isEnabled());
    }

    @Test
    void stageTddEnablesStartTdd() {
        SpecPipelineInstance inst = createInstance("auth", "req");
        service.advance(inst.getId(), PipelineStage.TDD, "tdd");
        refresh();
        selectFirst();

        assertFalse(panel.advancePlanItem.isEnabled());
        assertTrue(panel.startTddItem.isEnabled());
        assertFalse(panel.executeStepItem.isEnabled());
        assertFalse(panel.completeItem.isEnabled());
    }

    @Test
    void stageTddWithActiveStepEnablesExecuteStep() {
        SpecPipelineInstance inst = createInstance("auth", "req");
        service.advance(inst.getId(), PipelineStage.TDD, "tdd");
        service.addPlanStep(inst.getId(), "stop button");
        service.startStepTdd(inst.getId(), 0);
        refresh();
        selectFirst();

        assertTrue(panel.startTddItem.isEnabled());
        assertTrue(panel.executeStepItem.isEnabled());
    }

    @Test
    void stageChangeEnablesComplete() {
        SpecPipelineInstance inst = createInstance("auth", "req");
        service.advance(inst.getId(), PipelineStage.CHANGE, "change");
        refresh();
        selectFirst();

        assertFalse(panel.advancePlanItem.isEnabled());
        assertFalse(panel.startTddItem.isEnabled());
        assertTrue(panel.completeItem.isEnabled());
        assertTrue(panel.linkChangeItem.isEnabled());
    }

    // ---------- button actions ----------

    @Test
    void advancePlanSendsPipelineId() {
        SpecPipelineInstance inst = createInstance("auth", "req");
        refresh();
        selectFirst();

        panel.advancePlanItem.doClick();

        assertEquals("sdd_pipeline|{\"action\":\"advance_plan\",\"pipeline_id\":\"" + inst.getId() + "\"}",
                runner.lastCall());
    }

    @Test
    void startTddFromPlanStageStartsStepZero() {
        SpecPipelineInstance inst = createInstance("auth", "req");
        service.advance(inst.getId(), PipelineStage.PLAN, "planned");
        service.addPlanStep(inst.getId(), "step one");
        service.addPlanStep(inst.getId(), "step two");
        refresh();
        selectFirst();

        panel.startTddItem.doClick();

        JsonObject args = runner.lastArgs();
        assertEquals("start_tdd", args.get("action").getAsString());
        assertEquals(inst.getId(), args.get("pipeline_id").getAsString());
        assertEquals(0, args.get("step_index").getAsInt());
    }

    @Test
    void startTddFromTddStageAdvancesToNextStep() {
        SpecPipelineInstance inst = createInstance("auth", "req");
        service.advance(inst.getId(), PipelineStage.TDD, "tdd");
        service.addPlanStep(inst.getId(), "step one");
        service.addPlanStep(inst.getId(), "step two");
        service.startStepTdd(inst.getId(), 0);
        refresh();
        selectFirst();

        panel.startTddItem.doClick();

        JsonObject args = runner.lastArgs();
        assertEquals("start_tdd", args.get("action").getAsString());
        assertEquals(1, args.get("step_index").getAsInt());
    }

    @Test
    void startTddWithoutPlanStepsShowsHintWithoutToolCall() {
        SpecPipelineInstance inst = createInstance("auth", "req");
        service.advance(inst.getId(), PipelineStage.PLAN, "planned");
        refresh();
        selectFirst();

        panel.startTddItem.doClick();

        assertTrue(runner.calls.isEmpty(), "no tool call expected without plan steps");
        assertTrue(panel.output.getText().contains("No plan steps"));
    }

    @Test
    void startTddWhenAllStepsStartedShowsHint() {
        SpecPipelineInstance inst = createInstance("auth", "req");
        service.advance(inst.getId(), PipelineStage.TDD, "tdd");
        service.addPlanStep(inst.getId(), "step one");
        service.startStepTdd(inst.getId(), 0);
        refresh();
        selectFirst();

        panel.startTddItem.doClick();

        assertTrue(runner.calls.isEmpty(), "no tool call expected when all steps started");
        assertTrue(panel.output.getText().contains("All plan steps already started"));
    }

    @Test
    void completeSendsCompleteAction() {
        SpecPipelineInstance inst = createInstance("auth", "req");
        service.advance(inst.getId(), PipelineStage.CHANGE, "change");
        refresh();
        selectFirst();

        panel.completeItem.doClick();

        JsonObject args = runner.lastArgs();
        assertEquals("complete", args.get("action").getAsString());
        assertEquals(inst.getId(), args.get("pipeline_id").getAsString());
    }

    @Test
    void statusSendsPipelineIdOnly() {
        SpecPipelineInstance inst = createInstance("auth", "req");
        refresh();
        selectFirst();

        panel.statusItem.doClick();

        JsonObject args = runner.lastArgs();
        assertNull(args.get("action"), "status must not send an action");
        assertEquals(inst.getId(), args.get("pipeline_id").getAsString());
    }

    @Test
    void listSendsListActionWithoutPipelineId() {
        panel.listItem.doClick();

        JsonObject args = runner.lastArgs();
        assertEquals("list", args.get("action").getAsString());
        assertNull(args.get("pipeline_id"));
    }

    @Test
    void exportPlanInvokesExportPlanToolWithoutSelection() {
        panel.exportPlanItem.doClick();

        assertEquals("export_plan|{}", runner.lastCall());
    }

    @Test
    void selectionRequiredActionsAreDisabledWhenNothingSelected() {
        createInstance("auth", "req");
        refresh();
        panel.instanceTree.clearSelection();

        assertEquals("No active pipeline instance selected.", panel.summaryLabel.getText());
        assertFalse(panel.advancePlanItem.isEnabled());
        assertFalse(panel.startTddItem.isEnabled());
        assertFalse(panel.executeStepItem.isEnabled());
        assertFalse(panel.completeItem.isEnabled());
        assertFalse(panel.statusItem.isEnabled());
        assertFalse(panel.linkChangeItem.isEnabled());
        // Buttons that never require a selection stay enabled:
        assertTrue(panel.exportPlanItem.isEnabled());
        assertTrue(panel.listItem.isEnabled());
        assertTrue(panel.createChangeItem.isEnabled());
    }

    @Test
    void createChangeItemExplainsToUseTextChannel() {
        createInstance("auth", "req");
        refresh();
        selectFirst();

        panel.createChangeItem.doClick();

        assertTrue(runner.calls.isEmpty(), "manual buttons must not call tools");
        String text = panel.output.getText();
        assertTrue(text.contains("Create Change requires manual parameters"));
        assertTrue(text.contains("text command channel"));
    }

    @Test
    void linkChangeItemBindsChangeNameToPipeline() {
        SpecPipelineInstance inst = createInstance("auth", "req");
        refresh();
        selectFirst();
        panel.changeNamePrompt = current -> "stop-button-cancel";

        panel.linkChangeItem.doClick();

        JsonObject args = runner.lastArgs();
        assertNotNull(args);
        assertEquals("link_change", args.get("action").getAsString());
        assertEquals(inst.getId(), args.get("pipeline_id").getAsString());
        assertEquals("stop-button-cancel", args.get("change_name").getAsString());
    }

    @Test
    void toolExceptionIsCaughtAndShownAsError() {
        createInstance("auth", "req");
        refresh();
        selectFirst();
        runner.toThrow = new IllegalStateException("boom");

        panel.advancePlanItem.doClick();

        assertTrue(panel.output.getText().contains("Error: boom"), panel.output.getText());
    }

    @Test
    void toolResultIsAppendedToOutput() {
        SpecPipelineInstance inst = createInstance("auth", "req");
        refresh();
        selectFirst();
        runner.result = "PIPELINE RESULT: " + inst.getId();

        panel.statusItem.doClick();

        assertTrue(panel.output.getText().contains("PIPELINE RESULT: " + inst.getId()));
    }

    // ---------- TDD resume / reset decision ----------

    @Test
    void shouldResetTddForFreshDoneBlockedOrUnlinked() {
        assertTrue(PipelinePanel.shouldResetTdd(null, null, "p", null, "step task"));
        assertTrue(PipelinePanel.shouldResetTdd("IDLE", null, "p", null, "step task"));
        assertTrue(PipelinePanel.shouldResetTdd("DONE", null, "p", null, "step task"));
        assertTrue(PipelinePanel.shouldResetTdd("BLOCKED", null, "p", null, "step task"));
        // Mid-cycle but not linked to the selected pipeline: reset.
        assertTrue(PipelinePanel.shouldResetTdd("GREEN_VERIFY", "other-pipeline", "selected", "task", "step task"));
        // Mid-cycle with no link at all: cannot safely resume into the selected pipeline.
        assertTrue(PipelinePanel.shouldResetTdd("REFACTOR", null, "selected", "task", "step task"));
    }

    @Test
    void shouldNotResetForMidCycleLinkedToSelectedPipeline() {
        assertFalse(PipelinePanel.shouldResetTdd("GREEN_VERIFY", "p", "p", "t", "t"));
        assertFalse(PipelinePanel.shouldResetTdd("RED", "p", "p", "t", "t"));
        assertFalse(PipelinePanel.shouldResetTdd("REFACTOR", "p", "p", "t", "t"));
    }

    @Test
    void shouldResetWhenCycleTaskDoesNotMatchTheSelectedStepTask() {
        // A mid-cycle resumed cycle whose task text belongs to a different plan step must not be resumed:
        // it would keep the model working on the wrong deliverable.
        assertTrue(PipelinePanel.shouldResetTdd("RED", "p", "p", "Implement markdown formatting support in "
                + "MarkdownRenderer (headings, bold, italic) to satisfy MarkdownRendererTest",
                "实现纯函数 MarkdownRenderer.java（无 Swing 无 IO，仅 ASCII）"));
        assertTrue(PipelinePanel.shouldResetTdd("GREEN_VERIFY", "p", "p", "other task", "step task"));
    }

    @Test
    void buildResumeInstructionTellsModelToContinueNotStartOver() {
        String instruction = PipelinePanel.buildResumeInstruction("pipe-1", 1, 5, "GREEN_VERIFY");
        assertTrue(instruction.contains("RESUME the TDD cycle for pipeline pipe-1"), instruction);
        assertTrue(instruction.contains("1/5"), instruction);
        assertTrue(instruction.contains("GREEN_VERIFY"), instruction);
        assertTrue(instruction.contains("Do NOT call start_cycle"), instruction);
        assertTrue(instruction.contains("mark_refactor"), instruction);
    }

    // ---------- verify-step handling ----------

    @Test
    void isVerifyStepTrueOnlyForVerifyKind() {
        SpecPipelineInstance inst = createInstance("auth", "req");
        service.advance(inst.getId(), PipelineStage.TDD, "tdd");
        service.addPlanStep(inst.getId(), "code step");
        service.addPlanStep(inst.getId(), "final regression", "verify");
        service.startStepTdd(inst.getId(), 0);
        assertFalse(PipelinePanel.isVerifyStep(service.getById(inst.getId())));

        service.startStepTdd(inst.getId(), 1);
        assertTrue(PipelinePanel.isVerifyStep(service.getById(inst.getId())));
        assertFalse(PipelinePanel.isVerifyStep(null));
    }

    @Test
    void completeVerifyStepOnFinalStepAdvancesToChange() {
        SpecPipelineInstance inst = createInstance("auth", "req");
        service.advance(inst.getId(), PipelineStage.TDD, "tdd");
        service.addPlanStep(inst.getId(), "code step");
        service.addPlanStep(inst.getId(), "final regression", "verify");
        service.startStepTdd(inst.getId(), 1);

        String msg = panel.completeVerifyStep(service.getById(inst.getId()));

        assertEquals(PipelineStage.CHANGE, service.getById(inst.getId()).getCurrentStage());
        assertTrue(msg.contains("CHANGE"), msg);
    }

    @Test
    void completeVerifyStepOnNonFinalStepAdvancesToNext() {
        SpecPipelineInstance inst = createInstance("auth", "req");
        service.advance(inst.getId(), PipelineStage.TDD, "tdd");
        service.addPlanStep(inst.getId(), "final regression", "verify");
        service.addPlanStep(inst.getId(), "code step two");
        service.startStepTdd(inst.getId(), 0);

        String msg = panel.completeVerifyStep(service.getById(inst.getId()));

        assertEquals(1, service.getById(inst.getId()).getCurrentStepIndex());
        assertTrue(msg.contains("advanced to step 2"), msg);
    }
}
