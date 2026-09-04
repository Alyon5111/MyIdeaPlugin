package com.example.myplugin.agent.pipeline;

import com.example.myplugin.agent.tdd.TddEnforcerTool;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TddEnforcerPipelineIntegrationTest {

    private JsonObject args(String... kv) {
        JsonObject o = new JsonObject();
        for (int i = 0; i < kv.length; i += 2) {
            o.addProperty(kv[i], kv[i + 1]);
        }
        return o;
    }

    @Test
    void testTddDoneAdvancesPipeline() {
        PipelineService pipeline = PipelineService.inMemory();
        TddEnforcerTool tool = new TddEnforcerTool(null, pipeline);
        SpecPipelineInstance pi = pipeline.create("auth", "login requirement");

        // Set up pipeline in TDD stage with a single plan step active
        pipeline.addPlanStep(pi.getId(), "implement login");
        pipeline.advance(pi.getId(), PipelineStage.TDD, "tdd");
        pipeline.startStepTdd(pi.getId(), 0);

        // start TDD cycle (RED)
        String start = tool.execute(args("action", "start_cycle", "task", "write login test"));
        assertTrue(start.contains("RED"), start);

        // link pipeline
        String link = tool.execute(args("action", "link_pipeline", "pipeline_id", pi.getId()));
        assertTrue(link.contains("Linked TDD cycle"), link);

        // write test (RED -> RED_VERIFY)
        String wt = tool.execute(args("action", "write_test"));
        assertFalse(wt.startsWith("Error"), wt);

        // verify test fails as expected (RED_VERIFY -> GREEN)
        String vt = tool.execute(args("action", "verify_test", "test_output",
                "BUILD FAILED\n1 test failed: expected 2 but was 1"));
        assertFalse(vt.startsWith("Error"), vt);

        // write code (GREEN -> GREEN_VERIFY)
        String wc = tool.execute(args("action", "write_code"));
        assertFalse(wc.startsWith("Error"), wc);

        // verify code passes (GREEN_VERIFY -> REFACTOR)
        String vc = tool.execute(args("action", "verify_code", "test_output",
                "BUILD SUCCESSFUL\n3 tests passed"));
        assertFalse(vc.startsWith("Error"), vc);

        // mark refactor done (REFACTOR -> DONE) -> completes step; all done -> CHANGE
        String done = tool.execute(args("action", "mark_refactor"));
        assertTrue(done.contains("TDD Cycle Complete"), done);
        assertTrue(done.contains("CHANGE"), done);

        assertEquals(PipelineStage.CHANGE, pipeline.getById(pi.getId()).getCurrentStage());
    }

    @Test
    void testPipelineNotAdvancedUntilDone() {
        PipelineService pipeline = PipelineService.inMemory();
        TddEnforcerTool tool = new TddEnforcerTool(null, pipeline);
        SpecPipelineInstance pi = pipeline.create("auth", "login requirement");

        tool.execute(args("action", "start_cycle", "task", "t"));
        tool.execute(args("action", "link_pipeline", "pipeline_id", pi.getId()));

        // Do not complete the cycle. Pipeline should still be at SPEC.
        assertEquals(PipelineStage.SPEC, pipeline.getById(pi.getId()).getCurrentStage());
    }

    @Test
    void testLinkPipelineRequiresValidId() {
        PipelineService pipeline = PipelineService.inMemory();
        TddEnforcerTool tool = new TddEnforcerTool(null, pipeline);

        String result = tool.execute(args("action", "link_pipeline", "pipeline_id", "nonexistent"));
        assertTrue(result.contains("not found"), result);
    }
}
