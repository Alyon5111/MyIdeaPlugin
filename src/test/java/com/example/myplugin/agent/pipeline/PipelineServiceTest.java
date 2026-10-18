package com.example.myplugin.agent.pipeline;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PipelineServiceTest {

    private PipelineService newService() {
        return PipelineService.inMemory();
    }

    @Test
    void testCreateStartsWithSpecStage() {
        PipelineService service = newService();
        SpecPipelineInstance instance = service.create("auth", "Users can log in");

        assertNotNull(instance);
        assertNotNull(instance.getId());
        assertEquals(PipelineStage.SPEC, instance.getCurrentStage());
        assertEquals("Users can log in", instance.getRequirement());
        assertEquals(1, service.size());
    }

    @Test
    void testAdvanceThroughStages() {
        PipelineService service = newService();
        SpecPipelineInstance instance = service.create("auth", "requirement");

        service.advance(instance.getId(), PipelineStage.PLAN, "implementation plan");
        assertEquals(PipelineStage.PLAN, service.getById(instance.getId()).getCurrentStage());

        service.advance(instance.getId(), PipelineStage.TDD, "tdd step");
        assertEquals(PipelineStage.TDD, service.getById(instance.getId()).getCurrentStage());

        service.advance(instance.getId(), PipelineStage.CHANGE, "change created");
        assertEquals(PipelineStage.CHANGE, service.getById(instance.getId()).getCurrentStage());

        service.advance(instance.getId(), PipelineStage.DONE, "done");
        assertEquals(PipelineStage.DONE, service.getById(instance.getId()).getCurrentStage());
    }

    @Test
    void testLinkFields() {
        PipelineService service = newService();
        SpecPipelineInstance instance = service.create("auth", "requirement");

        service.addPlanStep(instance.getId(), "step one");
        service.linkPlan(instance.getId(), "2026-08-28-login-plan.md");
        service.linkChange(instance.getId(), "auth-login");

        SpecPipelineInstance fetched = service.getById(instance.getId());
        assertEquals(1, fetched.getPlanSteps().size());
        assertEquals("step one", fetched.getPlanSteps().get(0));
        assertEquals("2026-08-28-login-plan.md", fetched.getPlanFile());
        assertEquals("auth-login", fetched.getChangeName());
    }

    @Test
    void testPlanStepTddTracking() {
        PipelineService service = newService();
        SpecPipelineInstance instance = service.create("auth", "requirement");

        service.addPlanStep(instance.getId(), "step one");
        service.addPlanStep(instance.getId(), "step two");

        assertTrue(service.startStepTdd(instance.getId(), 0));
        assertEquals(0, service.getById(instance.getId()).getCurrentStepIndex());
        assertFalse(service.getById(instance.getId()).allStepsDone());

        assertTrue(service.completeStepTdd(instance.getId(), 0));
        assertFalse(service.getById(instance.getId()).allStepsDone());

        assertTrue(service.startStepTdd(instance.getId(), 1));
        assertTrue(service.completeStepTdd(instance.getId(), 1));
        assertTrue(service.getById(instance.getId()).allStepsDone());
    }

    @Test
    void testActiveExcludesDone() {
        PipelineService service = newService();
        SpecPipelineInstance a = service.create("a", "req1");
        SpecPipelineInstance b = service.create("b", "req2");
        service.advance(b.getId(), PipelineStage.DONE, "complete");

        assertEquals(1, service.getActive().size());
        assertEquals(a.getId(), service.getActive().get(0).getId());
    }

    @Test
    void testStageLogRecordsMovement() {
        PipelineService service = newService();
        SpecPipelineInstance instance = service.create("auth", "req");

        service.advance(instance.getId(), PipelineStage.TDD, "start");
        SpecPipelineInstance fetched = service.getById(instance.getId());
        assertTrue(fetched.getStageLog().size() >= 2);
        assertTrue(fetched.getStageLog().stream().anyMatch(l -> l.contains("ADVANCED to TDD")));
    }

    @Test
    void testGetByStage() {
        PipelineService service = newService();
        service.create("a", "r1");
        SpecPipelineInstance p2 = service.create("b", "r2");
        service.advance(p2.getId(), PipelineStage.CHANGE, "change");

        assertEquals(1, service.getByStage(PipelineStage.SPEC).size());
        assertEquals(1, service.getByStage(PipelineStage.CHANGE).size());
    }

    @Test
    void testSummaryFormat() {
        PipelineService service = newService();
        service.create("auth", "Users can login");
        String summary = service.formatSummary();
        assertTrue(summary.contains("SPEC"));
        assertTrue(summary.contains("Users can login"));
    }

    @Test
    void testSecondInstanceSeesPipelineCreatedByFirst() {
        List<SpecPipelineInstance> store = new ArrayList<>();
        PipelineService.Persistence shared = new PipelineService.Persistence() {
            @Override
            public List<SpecPipelineInstance> load() {
                return new ArrayList<>(store);
            }

            @Override
            public void save(List<SpecPipelineInstance> instances) {
                store.clear();
                store.addAll(instances);
            }
        };
        PipelineService ui = new PipelineService(shared);
        PipelineService agent = new PipelineService(shared);

        ui.create("chat-ui", "render markdown");

        assertTrue(agent.getActive().stream()
                .anyMatch(i -> "render markdown".equals(i.getRequirement())));
        assertTrue(agent.getAll().stream()
                .anyMatch(i -> "render markdown".equals(i.getRequirement())));
    }

    @Test
    void testAddPlanStepKindAndSetStepKind() {
        PipelineService service = newService();
        SpecPipelineInstance instance = service.create("auth", "req");
        service.addPlanStep(instance.getId(), "code step");
        service.addPlanStep(instance.getId(), "final regression", "verify");

        SpecPipelineInstance loaded = service.getById(instance.getId());
        assertEquals("code", loaded.stepKind(0));
        assertEquals("verify", loaded.stepKind(1));
        assertTrue(loaded.isVerifyStep(1));
        // Legacy entries with no stored kind default to "code" (never out of bounds):
        assertEquals("code", loaded.stepKind(99));

        assertTrue(service.setStepKind(instance.getId(), 0, "verify"));
        assertTrue(service.getById(instance.getId()).isVerifyStep(0));
        assertFalse(service.setStepKind(instance.getId(), 99, "verify"));
    }
}
