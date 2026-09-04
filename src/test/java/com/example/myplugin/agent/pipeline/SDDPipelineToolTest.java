package com.example.myplugin.agent.pipeline;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SDDPipelineToolTest {

    private SDDPipelineTool newTool() {
        return new SDDPipelineTool(PipelineService.inMemory());
    }

    @Test
    void testName() {
        assertEquals("sdd_pipeline", newTool().name());
    }

    @Test
    void testStart() {
        SDDPipelineTool tool = newTool();
        JsonObject args = new JsonObject();
        args.addProperty("action", "start");
        args.addProperty("requirement", "Users can log in");
        args.addProperty("spec_domain", "auth");

        String result = tool.execute(args);
        assertTrue(result.contains("SPEC PIPELINE STARTED"));
        assertTrue(result.contains("Users can log in"));
        assertTrue(result.contains("pipeline_id"));
    }

    @Test
    void testStartMissingRequirement() {
        SDDPipelineTool tool = newTool();
        JsonObject args = new JsonObject();
        args.addProperty("action", "start");
        String result = tool.execute(args);
        assertTrue(result.contains("requirement"));
    }

    @Test
    void testAddStepAndAdvancePlan() {
        PipelineService service = PipelineService.inMemory();
        SDDPipelineTool tool = new SDDPipelineTool(service);
        SpecPipelineInstance pi = service.create("auth", "req");

        JsonObject addStep = new JsonObject();
        addStep.addProperty("action", "add_step");
        addStep.addProperty("pipeline_id", pi.getId());
        addStep.addProperty("plan_steps", "step one\nstep two");
        String addResult = tool.execute(addStep);
        assertTrue(addResult.contains("Added 2 plan step(s)"), addResult);

        JsonObject adv = new JsonObject();
        adv.addProperty("action", "advance_plan");
        adv.addProperty("pipeline_id", pi.getId());
        adv.addProperty("plan_file", "2026-08-28-login-plan.md");
        String advResult = tool.execute(adv);
        assertEquals(PipelineStage.PLAN, service.getById(pi.getId()).getCurrentStage());
        assertTrue(advResult.contains("2026-08-28-login-plan.md"), advResult);
    }

    @Test
    void testAdvancePlanRequiresSteps() {
        PipelineService service = PipelineService.inMemory();
        SDDPipelineTool tool = new SDDPipelineTool(service);
        SpecPipelineInstance pi = service.create("auth", "req");

        JsonObject adv = new JsonObject();
        adv.addProperty("action", "advance_plan");
        adv.addProperty("pipeline_id", pi.getId());
        String advResult = tool.execute(adv);
        assertTrue(advResult.contains("Add plan steps"), advResult);
        assertEquals(PipelineStage.SPEC, service.getById(pi.getId()).getCurrentStage());
    }

    @Test
    void testStartTddAdvancesToTdd() {
        PipelineService service = PipelineService.inMemory();
        SDDPipelineTool tool = new SDDPipelineTool(service);
        SpecPipelineInstance pi = service.create("auth", "req");
        service.addPlanStep(pi.getId(), "step one");
        service.advance(pi.getId(), PipelineStage.PLAN, "p");

        JsonObject args = new JsonObject();
        args.addProperty("action", "start_tdd");
        args.addProperty("pipeline_id", pi.getId());
        args.addProperty("step_index", 0);

        String result = tool.execute(args);
        assertEquals(PipelineStage.TDD, service.getById(pi.getId()).getCurrentStage());
        assertTrue(result.contains("tdd_enforcer start_cycle"), result);
    }

    @Test
    void testCompleteTddFinalStepAdvancesToChange() {
        PipelineService service = PipelineService.inMemory();
        SDDPipelineTool tool = new SDDPipelineTool(service);
        SpecPipelineInstance pi = service.create("auth", "req");
        service.addPlanStep(pi.getId(), "step one");
        service.advance(pi.getId(), PipelineStage.TDD, "t");
        service.startStepTdd(pi.getId(), 0);

        JsonObject args = new JsonObject();
        args.addProperty("action", "complete_tdd");
        args.addProperty("pipeline_id", pi.getId());
        args.addProperty("step_index", 0);

        String result = tool.execute(args);
        assertEquals(PipelineStage.CHANGE, service.getById(pi.getId()).getCurrentStage());
        assertTrue(result.contains("ALL PLAN STEPS DONE"), result);
    }

    @Test
    void testCompleteTddMidStepStaysInTdd() {
        PipelineService service = PipelineService.inMemory();
        SDDPipelineTool tool = new SDDPipelineTool(service);
        SpecPipelineInstance pi = service.create("auth", "req");
        service.addPlanStep(pi.getId(), "step one");
        service.addPlanStep(pi.getId(), "step two");
        service.advance(pi.getId(), PipelineStage.TDD, "t");
        service.startStepTdd(pi.getId(), 0);

        JsonObject args = new JsonObject();
        args.addProperty("action", "complete_tdd");
        args.addProperty("pipeline_id", pi.getId());
        args.addProperty("step_index", 0);

        String result = tool.execute(args);
        assertEquals(PipelineStage.TDD, service.getById(pi.getId()).getCurrentStage());
        assertTrue(result.contains("next step"), result);
    }

    @Test
    void testLinkChangeAdvancesToDone() {
        PipelineService service = PipelineService.inMemory();
        SDDPipelineTool tool = new SDDPipelineTool(service);
        SpecPipelineInstance pi = service.create("auth", "req");
        service.advance(pi.getId(), PipelineStage.CHANGE, "c");

        JsonObject args = new JsonObject();
        args.addProperty("action", "link_change");
        args.addProperty("pipeline_id", pi.getId());
        args.addProperty("change_name", "auth-login");

        String result = tool.execute(args);
        assertEquals(PipelineStage.DONE, service.getById(pi.getId()).getCurrentStage());
        assertTrue(result.contains("auth-login"), result);
    }

    @Test
    void testComplete() {
        PipelineService service = PipelineService.inMemory();
        SDDPipelineTool tool = new SDDPipelineTool(service);
        SpecPipelineInstance pi = service.create("auth", "req");

        JsonObject args = new JsonObject();
        args.addProperty("action", "complete");
        args.addProperty("pipeline_id", pi.getId());
        String result = tool.execute(args);
        assertEquals(PipelineStage.DONE, service.getById(pi.getId()).getCurrentStage());
        assertTrue(result.contains("DONE"));
    }

    @Test
    void testNextStepGuidanceAtSpec() {
        PipelineService service = PipelineService.inMemory();
        SDDPipelineTool tool = new SDDPipelineTool(service);
        SpecPipelineInstance pi = service.create("auth", "req");

        JsonObject args = new JsonObject();
        args.addProperty("action", "next_step");
        args.addProperty("pipeline_id", pi.getId());
        String result = tool.execute(args);
        assertTrue(result.contains("add plan steps"), result);
    }

    @Test
    void testNextStepGuidanceAtTdd() {
        PipelineService service = PipelineService.inMemory();
        SDDPipelineTool tool = new SDDPipelineTool(service);
        SpecPipelineInstance pi = service.create("auth", "req");
        service.addPlanStep(pi.getId(), "step one");
        service.advance(pi.getId(), PipelineStage.TDD, "t");
        service.startStepTdd(pi.getId(), 0);

        JsonObject args = new JsonObject();
        args.addProperty("action", "next_step");
        args.addProperty("pipeline_id", pi.getId());
        String result = tool.execute(args);
        assertTrue(result.contains("tdd_enforcer"), result);
    }
}
