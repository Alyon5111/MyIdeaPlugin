package com.example.myplugin.ui;

import com.example.myplugin.agent.pipeline.PipelineService;
import com.example.myplugin.agent.pipeline.PipelineStage;
import com.example.myplugin.agent.pipeline.SpecPipelineInstance;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        assertFalse(panel.advancePlanButton.isEnabled());
        assertFalse(panel.startTddButton.isEnabled());
        assertFalse(panel.completeTddButton.isEnabled());
        assertFalse(panel.completeButton.isEnabled());
        assertFalse(panel.statusButton.isEnabled());
        assertFalse(panel.linkChangeButton.isEnabled());
        // Always available regardless of selection:
        assertTrue(panel.exportPlanButton.isEnabled());
        assertTrue(panel.listButton.isEnabled());
        assertTrue(panel.createChangeButton.isEnabled());
    }

    @Test
    void doneInstancesAreNotListed() {
        createInstance("auth", "active requirement");
        SpecPipelineInstance done = createInstance("billing", "finished requirement");
        service.advance(done.getId(), PipelineStage.DONE, "done");

        refresh();

        assertEquals(1, panel.instanceModel.size());
        assertEquals("[SPEC] active requirement  (auth)", panel.instanceModel.get(0));
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

        panel.instanceList.setSelectedIndex(0);
        assertTrue(panel.summaryLabel.getText().startsWith("[PLAN] step 1/2"));
        assertTrue(panel.summaryLabel.getText().contains("requirement for summary"));
    }

    // ---------- button enablement per stage ----------

    private void selectFirst() {
        panel.instanceList.setSelectedIndex(0);
    }

    @Test
    void stageSpecEnablesOnlyAdvancePlan() {
        createInstance("auth", "req");
        refresh();
        selectFirst();

        assertTrue(panel.advancePlanButton.isEnabled());
        assertFalse(panel.startTddButton.isEnabled());
        assertFalse(panel.completeTddButton.isEnabled());
        assertFalse(panel.completeButton.isEnabled());
        assertTrue(panel.statusButton.isEnabled());
    }

    @Test
    void stagePlanEnablesStartTddOnly() {
        SpecPipelineInstance inst = createInstance("auth", "req");
        service.advance(inst.getId(), PipelineStage.PLAN, "planned");
        refresh();
        selectFirst();

        // Advance Plan is SPEC->PLAN, so it is disabled once the instance is in PLAN.
        assertFalse(panel.advancePlanButton.isEnabled());
        assertTrue(panel.startTddButton.isEnabled());
        assertFalse(panel.completeTddButton.isEnabled());
        assertFalse(panel.completeButton.isEnabled());
    }

    @Test
    void stageTddEnablesStartAndCompleteTdd() {
        SpecPipelineInstance inst = createInstance("auth", "req");
        service.advance(inst.getId(), PipelineStage.TDD, "tdd");
        refresh();
        selectFirst();

        assertFalse(panel.advancePlanButton.isEnabled());
        assertTrue(panel.startTddButton.isEnabled());
        assertTrue(panel.completeTddButton.isEnabled());
        assertFalse(panel.completeButton.isEnabled());
    }

    @Test
    void stageChangeEnablesComplete() {
        SpecPipelineInstance inst = createInstance("auth", "req");
        service.advance(inst.getId(), PipelineStage.CHANGE, "change");
        refresh();
        selectFirst();

        assertFalse(panel.advancePlanButton.isEnabled());
        assertFalse(panel.startTddButton.isEnabled());
        assertFalse(panel.completeTddButton.isEnabled());
        assertTrue(panel.completeButton.isEnabled());
        assertTrue(panel.linkChangeButton.isEnabled());
    }

    // ---------- button actions ----------

    @Test
    void advancePlanSendsPipelineId() {
        SpecPipelineInstance inst = createInstance("auth", "req");
        refresh();
        selectFirst();

        panel.advancePlanButton.doClick();

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

        panel.startTddButton.doClick();

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

        panel.startTddButton.doClick();

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

        panel.startTddButton.doClick();

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

        panel.startTddButton.doClick();

        assertTrue(runner.calls.isEmpty(), "no tool call expected when all steps started");
        assertTrue(panel.output.getText().contains("All plan steps already started"));
    }

    @Test
    void completeTddSendsCurrentStepIndex() {
        SpecPipelineInstance inst = createInstance("auth", "req");
        service.advance(inst.getId(), PipelineStage.TDD, "tdd");
        service.addPlanStep(inst.getId(), "step one");
        service.addPlanStep(inst.getId(), "step two");
        service.startStepTdd(inst.getId(), 1);
        refresh();
        selectFirst();

        panel.completeTddButton.doClick();

        JsonObject args = runner.lastArgs();
        assertEquals("complete_tdd", args.get("action").getAsString());
        assertEquals(1, args.get("step_index").getAsInt());
    }

    @Test
    void completeSendsCompleteAction() {
        SpecPipelineInstance inst = createInstance("auth", "req");
        service.advance(inst.getId(), PipelineStage.CHANGE, "change");
        refresh();
        selectFirst();

        panel.completeButton.doClick();

        JsonObject args = runner.lastArgs();
        assertEquals("complete", args.get("action").getAsString());
        assertEquals(inst.getId(), args.get("pipeline_id").getAsString());
    }

    @Test
    void statusSendsPipelineIdOnly() {
        SpecPipelineInstance inst = createInstance("auth", "req");
        refresh();
        selectFirst();

        panel.statusButton.doClick();

        JsonObject args = runner.lastArgs();
        assertNull(args.get("action"), "status must not send an action");
        assertEquals(inst.getId(), args.get("pipeline_id").getAsString());
    }

    @Test
    void listSendsListActionWithoutPipelineId() {
        panel.listButton.doClick();

        JsonObject args = runner.lastArgs();
        assertEquals("list", args.get("action").getAsString());
        assertNull(args.get("pipeline_id"));
    }

    @Test
    void exportPlanInvokesExportPlanToolWithoutSelection() {
        panel.exportPlanButton.doClick();

        assertEquals("export_plan|{}", runner.lastCall());
    }

    @Test
    void selectionRequiredActionsAreDisabledWhenNothingSelected() {
        createInstance("auth", "req");
        refresh();
        panel.instanceList.clearSelection();

        assertEquals("No active pipeline instance selected.", panel.summaryLabel.getText());
        assertFalse(panel.advancePlanButton.isEnabled());
        assertFalse(panel.startTddButton.isEnabled());
        assertFalse(panel.completeTddButton.isEnabled());
        assertFalse(panel.completeButton.isEnabled());
        assertFalse(panel.statusButton.isEnabled());
        assertFalse(panel.linkChangeButton.isEnabled());
        // Buttons that never require a selection stay enabled:
        assertTrue(panel.exportPlanButton.isEnabled());
        assertTrue(panel.listButton.isEnabled());
        assertTrue(panel.createChangeButton.isEnabled());
    }

    @Test
    void manualButtonsExplainToUseTextChannel() {
        createInstance("auth", "req");
        refresh();
        selectFirst();

        panel.createChangeButton.doClick();
        panel.linkChangeButton.doClick();

        assertTrue(runner.calls.isEmpty(), "manual buttons must not call tools");
        String text = panel.output.getText();
        assertTrue(text.contains("Create Change requires manual parameters"));
        assertTrue(text.contains("Link Change requires manual parameters"));
        assertTrue(text.contains("text command channel"));
    }

    @Test
    void toolExceptionIsCaughtAndShownAsError() {
        createInstance("auth", "req");
        refresh();
        selectFirst();
        runner.toThrow = new IllegalStateException("boom");

        panel.advancePlanButton.doClick();

        assertTrue(panel.output.getText().contains("Error: boom"), panel.output.getText());
    }

    @Test
    void toolResultIsAppendedToOutput() {
        SpecPipelineInstance inst = createInstance("auth", "req");
        refresh();
        selectFirst();
        runner.result = "PIPELINE RESULT: " + inst.getId();

        panel.statusButton.doClick();

        assertTrue(panel.output.getText().contains("PIPELINE RESULT: " + inst.getId()));
    }
}
