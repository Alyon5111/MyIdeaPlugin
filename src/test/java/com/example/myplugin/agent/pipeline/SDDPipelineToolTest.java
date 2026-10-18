package com.example.myplugin.agent.pipeline;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SDDPipelineToolTest {

    private SDDPipelineTool newTool() {
        return new SDDPipelineTool(PipelineService.inMemory());
    }

    @TempDir
    Path tempDir;

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
    void testStartWritesSpecDocToDisk() throws Exception {
        SDDPipelineTool tool = new SDDPipelineTool(PipelineService.inMemory(), tempDir);
        JsonObject args = new JsonObject();
        args.addProperty("action", "start");
        args.addProperty("requirement", "Users can log in");
        args.addProperty("spec_domain", "auth");

        String result = tool.execute(args);
        assertTrue(result.contains("Spec doc: openspec/specs/auth/spec.md"), result);

        Path specFile = tempDir.resolve("openspec/specs/auth/spec.md");
        assertTrue(Files.exists(specFile), "spec.md should be written to disk");
        String content = Files.readString(specFile);
        assertTrue(content.contains("# Auth"), content);
        assertTrue(content.contains("## Purpose"), content);
        assertTrue(content.contains("### Requirement: Users can log in"), content);
        assertTrue(content.contains("#### Scenario"), content);
    }

    @Test
    void testStartWithExistingSpecDocDoesNotOverwrite() throws Exception {
        Path specDir = tempDir.resolve("openspec/specs/auth");
        Files.createDirectories(specDir);
        Files.writeString(specDir.resolve("spec.md"), "# Original");

        SDDPipelineTool tool = new SDDPipelineTool(PipelineService.inMemory(), tempDir);
        JsonObject args = new JsonObject();
        args.addProperty("action", "start");
        args.addProperty("requirement", "Users can log in");
        args.addProperty("spec_domain", "auth");

        String result = tool.execute(args);
        assertTrue(result.contains("already exists"), result);
        assertEquals("# Original", Files.readString(specDir.resolve("spec.md")));
    }

    @Test
    void testStartWithoutSpecDomainSkipsSpecDoc() {
        SDDPipelineTool tool = new SDDPipelineTool(PipelineService.inMemory(), tempDir);
        JsonObject args = new JsonObject();
        args.addProperty("action", "start");
        args.addProperty("requirement", "Users can log in");

        String result = tool.execute(args);
        assertTrue(result.contains("no spec_domain given"), result);
        assertFalse(Files.exists(tempDir.resolve("openspec/specs")));
    }

    @Test
    void buildSpecContentAddsOptionalChineseNote() {
        String content = SDDPipelineTool.buildSpecContent("auth", "Users can log in", "用户登录");
        assertTrue(content.contains("### Requirement: Users can log in"), content);
        assertTrue(content.contains("> Note (Chinese): 用户登录"), content);
    }

    @Test
    void buildSpecContentOmitsChineseNoteWhenAbsent() {
        String content = SDDPipelineTool.buildSpecContent("auth", "Users can log in", "");
        assertFalse(content.contains("Note (Chinese)"), content);
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
    void testAdvancePlanGeneratesPlanFileWhenMissing() throws Exception {
        PipelineService service = PipelineService.inMemory();
        SDDPipelineTool tool = new SDDPipelineTool(service, tempDir);
        SpecPipelineInstance pi = service.create("agent-tools", "Add export plan tool");
        service.addPlanStep(pi.getId(), "step one");

        JsonObject adv = new JsonObject();
        adv.addProperty("action", "advance_plan");
        adv.addProperty("pipeline_id", pi.getId());
        String advResult = tool.execute(adv);
        assertEquals(PipelineStage.PLAN, service.getById(pi.getId()).getCurrentStage());
        assertTrue(advResult.contains("Plan file: "), advResult);
        assertTrue(advResult.contains("-plan.md"), advResult);

        Path plansDir = tempDir.resolve("docs/plans");
        assertTrue(Files.exists(plansDir), "plans dir should exist");
        try (var stream = Files.list(plansDir)) {
            assertTrue(stream.anyMatch(p -> p.getFileName().toString().endsWith("-plan.md")),
                    "a plan file should be written to docs/plans");
        }
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
        service.recordTddEvidence(pi.getId(), 0);

        JsonObject args = new JsonObject();
        args.addProperty("action", "complete_tdd");
        args.addProperty("pipeline_id", pi.getId());
        args.addProperty("step_index", 0);

        String result = tool.execute(args);
        assertEquals(PipelineStage.CHANGE, service.getById(pi.getId()).getCurrentStage());
        assertTrue(result.contains("ALL PLAN STEPS DONE"), result);
    }

    @Test
    void testCompleteTddRequiresEvidence() {
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
        assertTrue(result.contains("no TDD evidence"), result);
        assertEquals(PipelineStage.TDD, service.getById(pi.getId()).getCurrentStage());
        assertFalse(service.getById(pi.getId()).getStageLog().stream()
                .anyMatch(l -> l.contains("TDD DONE")));
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
        service.recordTddEvidence(pi.getId(), 0);

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

    @Test
    void testStatusWithoutIdPrintsAllActiveWithIds() {
        PipelineService service = PipelineService.inMemory();
        SDDPipelineTool tool = new SDDPipelineTool(service);
        SpecPipelineInstance pi1 = service.create("auth", "req1");
        SpecPipelineInstance pi2 = service.create("agent-tools", "req2");

        JsonObject args = new JsonObject();
        args.addProperty("action", "status");
        String result = tool.execute(args);
        assertTrue(result.contains("ID: " + pi1.getId()), result);
        assertTrue(result.contains("ID: " + pi2.getId()), result);
        assertTrue(result.contains("PIPELINE INSTANCE"), result);
    }

    @Test
    void testStatusWithoutIdWhenEmpty() {
        PipelineService service = PipelineService.inMemory();
        SDDPipelineTool tool = new SDDPipelineTool(service);

        JsonObject args = new JsonObject();
        args.addProperty("action", "status");
        String result = tool.execute(args);
        assertTrue(result.contains("No active pipeline instances"), result);
    }

    @Test
    void testStatusWithoutIdExcludesDone() {
        PipelineService service = PipelineService.inMemory();
        SDDPipelineTool tool = new SDDPipelineTool(service);
        SpecPipelineInstance active = service.create("auth", "req1");
        SpecPipelineInstance done = service.create("auth", "req2");
        service.advance(done.getId(), PipelineStage.DONE, "d");

        JsonObject args = new JsonObject();
        args.addProperty("action", "status");
        String result = tool.execute(args);
        assertTrue(result.contains("ID: " + active.getId()), result);
        assertFalse(result.contains("ID: " + done.getId()), result);
    }

    @Test
    void testAddStepMarksVerifyPrefix() {
        PipelineService service = PipelineService.inMemory();
        SDDPipelineTool tool = new SDDPipelineTool(service);
        SpecPipelineInstance pi = service.create("auth", "req");

        JsonObject addStep = new JsonObject();
        addStep.addProperty("action", "add_step");
        addStep.addProperty("pipeline_id", pi.getId());
        addStep.addProperty("plan_steps", "implement feature\n[verify] regression gate");
        tool.execute(addStep);

        SpecPipelineInstance loaded = service.getById(pi.getId());
        assertEquals(2, loaded.getPlanSteps().size());
        assertEquals("implement feature", loaded.getPlanSteps().get(0));
        assertEquals("code", loaded.stepKind(0));
        assertEquals("regression gate", loaded.getPlanSteps().get(1));
        assertTrue(loaded.isVerifyStep(1));
    }

    @Test
    void testSetStepKindAction() {
        PipelineService service = PipelineService.inMemory();
        SDDPipelineTool tool = new SDDPipelineTool(service);
        SpecPipelineInstance pi = service.create("auth", "req");
        service.addPlanStep(pi.getId(), "step one");

        JsonObject args = new JsonObject();
        args.addProperty("action", "set_step_kind");
        args.addProperty("pipeline_id", pi.getId());
        args.addProperty("step_index", 0);
        args.addProperty("kind", "verify");
        String result = tool.execute(args);
        assertTrue(result.contains("verify"), result);
        assertTrue(service.getById(pi.getId()).isVerifyStep(0));
    }

    @Test
    void testSetStepKindRejectsUnknownKind() {
        PipelineService service = PipelineService.inMemory();
        SDDPipelineTool tool = new SDDPipelineTool(service);
        SpecPipelineInstance pi = service.create("auth", "req");
        service.addPlanStep(pi.getId(), "step one");

        JsonObject args = new JsonObject();
        args.addProperty("action", "set_step_kind");
        args.addProperty("pipeline_id", pi.getId());
        args.addProperty("step_index", 0);
        args.addProperty("kind", "bogus");
        String result = tool.execute(args);
        assertTrue(result.contains("'kind' must be"), result);
        assertFalse(service.getById(pi.getId()).isVerifyStep(0));
    }
}
