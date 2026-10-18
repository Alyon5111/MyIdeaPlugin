package com.example.myplugin.agent.tdd;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.pipeline.PipelineService;
import com.example.myplugin.agent.pipeline.PipelineStage;
import com.example.myplugin.agent.pipeline.SpecPipelineInstance;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TddEnforcerToolTest {

    @Test
    void startCycleWithPipelineIdLinksAndAdvanceOnRefactor() {
        PipelineService pipeline = PipelineService.inMemory();
        SpecPipelineInstance instance = pipeline.create("agent-tools", "export plan");
        instance.setStage(PipelineStage.TDD, "null");
        pipeline.addPlanStep(instance.getId(), "step one");
        pipeline.startStepTdd(instance.getId(), 0);

        TddEnforcerTool tool = new TddEnforcerTool(null, pipeline);

        JsonObject start = new JsonObject();
        start.addProperty("action", "start_cycle");
        start.addProperty("task", "t");
        start.addProperty("pipeline_id", instance.getId());
        String started = tool.execute(start);
        assertTrue(started.contains("=== TDD Cycle Started ==="), started);

        JsonObject writeTest = new JsonObject();
        writeTest.addProperty("action", "write_test");
        String wt = tool.execute(writeTest);
        assertFalse(wt.contains("Error"), wt);
        assertTrue(wt.contains("Write a failing test"), wt);

        JsonObject verifyTest = new JsonObject();
        verifyTest.addProperty("action", "verify_test");
        verifyTest.addProperty("test_output", "BUILD FAILED\n1 test failed");
        String vt = tool.execute(verifyTest);
        assertTrue(vt.contains("Test fails as expected"), vt);

        JsonObject writeCode = new JsonObject();
        writeCode.addProperty("action", "write_code");
        String wc = tool.execute(writeCode);
        assertFalse(wc.contains("Error"), wc);
        assertTrue(wc.contains("Write minimal code"), wc);

        JsonObject verifyCode = new JsonObject();
        verifyCode.addProperty("action", "verify_code");
        verifyCode.addProperty("test_output", "BUILD SUCCESSFUL\n1 test passed");
        String vc = tool.execute(verifyCode);
        assertTrue(vc.contains("All tests pass"), vc);

        JsonObject markRefactor = new JsonObject();
        markRefactor.addProperty("action", "mark_refactor");
        String done = tool.execute(markRefactor);
        assertTrue(done.contains("Spec pipeline"), done);

        SpecPipelineInstance after = pipeline.getById(instance.getId());
        assertEquals(PipelineStage.CHANGE, after.getCurrentStage());
    }

    @Test
    void linkPipelineActionStillWorks() {
        PipelineService pipeline = PipelineService.inMemory();
        SpecPipelineInstance instance = pipeline.create("agent-tools", "export plan");
        TddEnforcerTool tool = new TddEnforcerTool(null, pipeline);

        JsonObject link = new JsonObject();
        link.addProperty("action", "link_pipeline");
        link.addProperty("pipeline_id", instance.getId());
        String result = tool.execute(link);
        assertEquals("Linked TDD cycle to spec pipeline " + instance.getId() + ".", result);
    }

    @Test
    void markRefactorAdvancesToNextPlanStep() {
        PipelineService pipeline = PipelineService.inMemory();
        SpecPipelineInstance instance = pipeline.create("agent-tools", "export plan");
        instance.setStage(PipelineStage.TDD, "null");
        pipeline.addPlanStep(instance.getId(), "step one");
        pipeline.addPlanStep(instance.getId(), "step two");
        pipeline.startStepTdd(instance.getId(), 0);

        TddEnforcerTool tool = new TddEnforcerTool(null, pipeline);

        JsonObject start = new JsonObject();
        start.addProperty("action", "start_cycle");
        start.addProperty("task", "t");
        start.addProperty("pipeline_id", instance.getId());
        tool.execute(start);

        JsonObject writeTest = new JsonObject();
        writeTest.addProperty("action", "write_test");
        tool.execute(writeTest);

        JsonObject verifyTest = new JsonObject();
        verifyTest.addProperty("action", "verify_test");
        verifyTest.addProperty("test_output", "BUILD FAILED\n1 test failed");
        tool.execute(verifyTest);

        JsonObject writeCode = new JsonObject();
        writeCode.addProperty("action", "write_code");
        tool.execute(writeCode);

        JsonObject verifyCode = new JsonObject();
        verifyCode.addProperty("action", "verify_code");
        verifyCode.addProperty("test_output", "BUILD SUCCESSFUL\n1 test passed");
        tool.execute(verifyCode);

        JsonObject markRefactor = new JsonObject();
        markRefactor.addProperty("action", "mark_refactor");
        String done = tool.execute(markRefactor);
        assertTrue(done.contains("step 1 TDD done"), done);
        assertTrue(done.contains("Current step advanced to step 2"), done);

        SpecPipelineInstance after = pipeline.getById(instance.getId());
        assertEquals(1, after.getCurrentStepIndex());
        assertTrue(after.hasTddEvidence(0));
        assertFalse(after.hasTddEvidence(1));
    }

    @Test
    void markRefactorOnLastStepAdvancesToChangeInsteadOfNextStep() {
        PipelineService pipeline = PipelineService.inMemory();
        SpecPipelineInstance instance = pipeline.create("agent-tools", "export plan");
        instance.setStage(PipelineStage.TDD, "null");
        pipeline.addPlanStep(instance.getId(), "only step");
        pipeline.startStepTdd(instance.getId(), 0);

        TddEnforcerTool tool = new TddEnforcerTool(null, pipeline);

        JsonObject start = new JsonObject();
        start.addProperty("action", "start_cycle");
        start.addProperty("task", "t");
        start.addProperty("pipeline_id", instance.getId());
        tool.execute(start);

        JsonObject writeTest = new JsonObject();
        writeTest.addProperty("action", "write_test");
        tool.execute(writeTest);

        JsonObject verifyTest = new JsonObject();
        verifyTest.addProperty("action", "verify_test");
        verifyTest.addProperty("test_output", "BUILD FAILED\n1 test failed");
        tool.execute(verifyTest);

        JsonObject writeCode = new JsonObject();
        writeCode.addProperty("action", "write_code");
        tool.execute(writeCode);

        JsonObject verifyCode = new JsonObject();
        verifyCode.addProperty("action", "verify_code");
        verifyCode.addProperty("test_output", "BUILD SUCCESSFUL\n1 test passed");
        tool.execute(verifyCode);

        JsonObject markRefactor = new JsonObject();
        markRefactor.addProperty("action", "mark_refactor");
        String done = tool.execute(markRefactor);
        assertTrue(done.contains("ALL plan steps done"), done);

        SpecPipelineInstance after = pipeline.getById(instance.getId());
        assertEquals(PipelineStage.CHANGE, after.getCurrentStage());
    }

    @Test
    void startCycleWithOffStepTaskSurfacesMismatchNote() {
        PipelineService pipeline = PipelineService.inMemory();
        SpecPipelineInstance instance = pipeline.create("agent-tools", "export plan");
        instance.setStage(PipelineStage.TDD, "null");
        pipeline.addPlanStep(instance.getId(), "step one");
        pipeline.addPlanStep(instance.getId(), "step two");
        pipeline.startStepTdd(instance.getId(), 1);

        TddEnforcerTool tool = new TddEnforcerTool(null, pipeline);

        JsonObject start = new JsonObject();
        start.addProperty("action", "start_cycle");
        start.addProperty("task", "unrelated off-step task");
        start.addProperty("pipeline_id", instance.getId());
        String started = tool.execute(start);
        assertTrue(started.contains("does NOT match the current plan step"), started);
        assertTrue(started.contains("step two"), started);

        JsonObject status = new JsonObject();
        status.addProperty("action", "get_status");
        String statusResult = tool.execute(status);
        assertTrue(statusResult.contains("does NOT match the current plan step"), statusResult);
    }

    @Test
    void startCycleWithMatchingStepTaskHasNoMismatchNote() {
        PipelineService pipeline = PipelineService.inMemory();
        SpecPipelineInstance instance = pipeline.create("agent-tools", "export plan");
        instance.setStage(PipelineStage.TDD, "null");
        pipeline.addPlanStep(instance.getId(), "step one");
        pipeline.addPlanStep(instance.getId(), "step two");
        pipeline.startStepTdd(instance.getId(), 1);

        TddEnforcerTool tool = new TddEnforcerTool(null, pipeline);

        JsonObject start = new JsonObject();
        start.addProperty("action", "start_cycle");
        start.addProperty("task", "step two");
        start.addProperty("pipeline_id", instance.getId());
        String started = tool.execute(start);
        assertTrue(started.contains("step two"), started);
        assertFalse(started.contains("does NOT match"), started);
    }

    @Test
    void startCycleWithUnknownPipelineIdReturnsErrorWithoutLinking() {
        PipelineService pipeline = PipelineService.inMemory();
        TddEnforcerTool tool = new TddEnforcerTool(null, pipeline);

        JsonObject start = new JsonObject();
        start.addProperty("action", "start_cycle");
        start.addProperty("task", "t");
        start.addProperty("pipeline_id", "does-not-exist");
        String result = tool.execute(start);
        assertTrue(result.contains("Pipeline instance not found"), result);
    }

    @Test
    void verifyTestInRedAutoAdvancesWithoutWriteTest() {
        TddEnforcerTool tool = new TddEnforcerTool(null, null);

        JsonObject start = new JsonObject();
        start.addProperty("action", "start_cycle");
        start.addProperty("task", "t");
        tool.execute(start);

        JsonObject verify = new JsonObject();
        verify.addProperty("action", "verify_test");
        verify.addProperty("test_output", "BUILD FAILED\n1 test failed");
        String result = tool.execute(verify);
        assertTrue(result.contains("Test fails as expected"), result);
        assertTrue(result.contains("GREEN"), result);
    }

    @Test
    void verifyCodeInGreenAutoAdvancesWithoutWriteCode() {
        TddEnforcerTool tool = new TddEnforcerTool(null, null);

        JsonObject start = new JsonObject();
        start.addProperty("action", "start_cycle");
        start.addProperty("task", "t");
        tool.execute(start);

        JsonObject verifyTest = new JsonObject();
        verifyTest.addProperty("action", "verify_test");
        verifyTest.addProperty("test_output", "BUILD FAILED\n1 test failed");
        tool.execute(verifyTest);

        JsonObject verifyCode = new JsonObject();
        verifyCode.addProperty("action", "verify_code");
        verifyCode.addProperty("test_output", "BUILD SUCCESSFUL\n1 test passed");
        String result = tool.execute(verifyCode);
        assertTrue(result.contains("All tests pass"), result);
    }

    @Test
    void verifyTestInGreenRejectsWithNextActionGuidance() {
        TddEnforcerTool tool = new TddEnforcerTool(null, null);

        JsonObject start = new JsonObject();
        start.addProperty("action", "start_cycle");
        start.addProperty("task", "t");
        tool.execute(start);

        JsonObject verifyTest = new JsonObject();
        verifyTest.addProperty("action", "verify_test");
        verifyTest.addProperty("test_output", "BUILD FAILED\n1 test failed");
        tool.execute(verifyTest);

        JsonObject reVerify = new JsonObject();
        reVerify.addProperty("action", "verify_test");
        reVerify.addProperty("test_output", "BUILD FAILED\n1 test failed");
        String result = tool.execute(reVerify);
        assertTrue(result.contains("do NOT re-verify"), result);
        assertTrue(result.contains("NEXT ACTION: call action=write_code"), result);
        assertTrue(result.contains("GREEN"), result);
    }

    @Test
    void getStatusAndEveryTransitionCarryNextActionFooter() {
        TddEnforcerTool tool = new TddEnforcerTool(null, null);

        JsonObject start = new JsonObject();
        start.addProperty("action", "start_cycle");
        start.addProperty("task", "t");
        assertTrue(tool.execute(start).contains("NEXT ACTION"));
        assertTrue(tool.execute(status()).contains("NEXT ACTION"));
    }

    @Test
    void repeatingTaskThatPreviouslyBlockedSurfacesAddNewFailingTestNote() {
        TddEnforcerTool tool = new TddEnforcerTool(null, null);

        JsonObject start = new JsonObject();
        start.addProperty("action", "start_cycle");
        start.addProperty("task", "same task");
        assertFalse(tool.execute(start).contains("ADD ONE new failing test"),
                "first attempt must not carry the note");

        JsonObject verifyPassing = new JsonObject();
        verifyPassing.addProperty("action", "verify_test");
        verifyPassing.addProperty("test_output", "BUILD SUCCESSFUL\n1 test passed");
        assertTrue(tool.execute(verifyPassing).contains("TDD VIOLATION"),
                "same-task cycle must block on passing tests");

        JsonObject cancel = new JsonObject();
        cancel.addProperty("action", "cancel");
        tool.execute(cancel);

        JsonObject restart = new JsonObject();
        restart.addProperty("action", "start_cycle");
        restart.addProperty("task", "same task");
        String restarted = tool.execute(restart);
        assertTrue(restarted.contains("KEEP the verified assertions"), restarted);
        assertTrue(restarted.contains("ADD ONE new failing test"), restarted);

        String status = tool.execute(status());
        assertTrue(status.contains("ADD ONE new failing test"), status);
    }

    @Test
    void unrelatedNewTaskCarriesNoBlockedTaskNote() {
        TddEnforcerTool tool = new TddEnforcerTool(null, null);

        JsonObject start = new JsonObject();
        start.addProperty("action", "start_cycle");
        start.addProperty("task", "first task");
        tool.execute(start);

        JsonObject verifyPassing = new JsonObject();
        verifyPassing.addProperty("action", "verify_test");
        verifyPassing.addProperty("test_output", "BUILD SUCCESSFUL\n1 test passed");
        tool.execute(verifyPassing);

        JsonObject cancel = new JsonObject();
        cancel.addProperty("action", "cancel");
        tool.execute(cancel);

        JsonObject restart = new JsonObject();
        restart.addProperty("action", "start_cycle");
        restart.addProperty("task", "different task");
        String restarted = tool.execute(restart);
        assertFalse(restarted.contains("ADD ONE new failing test"), restarted);
    }

    private JsonObject status() {
        JsonObject s = new JsonObject();
        s.addProperty("action", "get_status");
        return s;
    }

    @Test
    void persistedGreenVerifyIsRestoredAndCanResumeToCompletion() {
        PipelineService pipeline = PipelineService.inMemory();
        SpecPipelineInstance instance = pipeline.create("agent-tools", "export plan");
        instance.setStage(PipelineStage.TDD, "null");
        pipeline.addPlanStep(instance.getId(), "step one");
        pipeline.startStepTdd(instance.getId(), 0);

        TddStateSnapshot snapshot = new TddStateSnapshot();
        snapshot.setState("GREEN_VERIFY");
        snapshot.setCycleCount(1);
        snapshot.setCurrentTask("task t");
        snapshot.setLinkedPipelineId(instance.getId());
        snapshot.setHistory(List.of("CYCLE 1: Started task 't' -> RED", "GREEN: Writing minimal implementation"));

        Path file = java.nio.file.Path.of(tempDir.toString(), "state.json");
        TddEnforcerStorage.save(file, snapshot);

        TddEnforcerTool tool = new TddEnforcerTool(null, pipeline, file);

        String status = tool.execute(status());
        assertTrue(status.contains("GREEN_VERIFY"), status);
        assertTrue(status.contains("task t"), status);

        JsonObject verifyCode = new JsonObject();
        verifyCode.addProperty("action", "verify_code");
        verifyCode.addProperty("test_output", "BUILD SUCCESSFUL\n1 test passed");
        String vc = tool.execute(verifyCode);
        assertTrue(vc.contains("All tests pass"), vc);

        JsonObject markRefactor = new JsonObject();
        markRefactor.addProperty("action", "mark_refactor");
        String done = tool.execute(markRefactor);
        assertTrue(done.contains("Spec pipeline"), done);

        SpecPipelineInstance after = pipeline.getById(instance.getId());
        assertTrue(after.hasTddEvidence(0), "resumed cycle must record pipeline evidence");
    }

    @Test
    void transitionsPersistSnapshotToStorage() {
        PipelineService pipeline = PipelineService.inMemory();
        SpecPipelineInstance instance = pipeline.create("agent-tools", "export plan");
        instance.setStage(PipelineStage.TDD, "null");

        Path file = java.nio.file.Path.of(tempDir.toString(), "state.json");
        TddEnforcerTool tool = new TddEnforcerTool(null, pipeline, file);

        JsonObject start = new JsonObject();
        start.addProperty("action", "start_cycle");
        start.addProperty("task", "t");
        start.addProperty("pipeline_id", instance.getId());
        tool.execute(start);

        TddStateSnapshot afterStart = TddEnforcerStorage.load(file);
        assertNotNull(afterStart);
        assertEquals("RED", afterStart.getState());
        assertEquals(instance.getId(), afterStart.getLinkedPipelineId());

        JsonObject verifyTest = new JsonObject();
        verifyTest.addProperty("action", "verify_test");
        verifyTest.addProperty("test_output", "BUILD FAILED\n2 tests failed");
        tool.execute(verifyTest);

        JsonObject writeCode = new JsonObject();
        writeCode.addProperty("action", "write_code");
        tool.execute(writeCode);

        TddStateSnapshot afterWrite = TddEnforcerStorage.load(file);
        assertNotNull(afterWrite);
        assertEquals("GREEN_VERIFY", afterWrite.getState());
        assertEquals(instance.getId(), afterWrite.getLinkedPipelineId());
        assertEquals(4, afterWrite.getHistory().size());
    }

    @Test
    void linkedPipelineIdGetterReflectsRestore() {
        PipelineService pipeline = PipelineService.inMemory();
        SpecPipelineInstance instance = pipeline.create("agent-tools", "export plan");

        TddStateSnapshot snapshot = new TddStateSnapshot();
        snapshot.setState("REFACTOR");
        snapshot.setCycleCount(1);
        snapshot.setLinkedPipelineId(instance.getId());
        snapshot.setHistory(List.of());

        Path file = java.nio.file.Path.of(tempDir.toString(), "state.json");
        TddEnforcerStorage.save(file, snapshot);

        TddEnforcerTool tool = new TddEnforcerTool(null, pipeline, file);
        assertEquals(instance.getId(), tool.linkedPipelineId());
    }

    @Test
    void startCycleWhileActiveOffersStateGuidanceNotCancelDemand() {
        TddEnforcerTool tool = new TddEnforcerTool(null, null);

        JsonObject start = new JsonObject();
        start.addProperty("action", "start_cycle");
        start.addProperty("task", "t");
        tool.execute(start);

        JsonObject again = new JsonObject();
        again.addProperty("action", "start_cycle");
        again.addProperty("task", "t");
        String result = tool.execute(again);
        assertTrue(result.contains("already in progress"), result);
        assertTrue(result.contains("NEXT ACTION"), result);
        assertFalse(result.contains("Cancel it first"), "start_cycle must not demand cancel on a live cycle");
    }

    @TempDir
    java.nio.file.Path tempDir;
}