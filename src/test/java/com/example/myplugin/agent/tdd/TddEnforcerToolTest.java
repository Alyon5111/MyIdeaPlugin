package com.example.myplugin.agent.tdd;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.pipeline.PipelineService;
import com.example.myplugin.agent.pipeline.PipelineStage;
import com.example.myplugin.agent.pipeline.SpecPipelineInstance;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

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
}