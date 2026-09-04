package com.example.myplugin.agent.pipeline;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PipelineService {

    @FunctionalInterface
    public interface Persistence {
        List<SpecPipelineInstance> load();
        default void save(List<SpecPipelineInstance> instances) {}
    }

    private final Persistence persistence;
    private final List<SpecPipelineInstance> instances;
    private final Object lock = new Object();

    public PipelineService(@NotNull Project project) {
        this(new Persistence() {
            @Override
            public List<SpecPipelineInstance> load() {
                return PipelineStorageService.load(project);
            }

            @Override
            public void save(List<SpecPipelineInstance> instances) {
                PipelineStorageService.save(project, instances);
            }
        });
    }

    public PipelineService(Persistence persistence) {
        this.persistence = persistence;
        this.instances = new ArrayList<>(persistence.load());
    }

    public static PipelineService inMemory() {
        List<SpecPipelineInstance> store = new ArrayList<>();
        return new PipelineService(new Persistence() {
            @Override
            public List<SpecPipelineInstance> load() {
                return new ArrayList<>(store);
            }

            @Override
            public void save(List<SpecPipelineInstance> instances) {
                store.clear();
                store.addAll(instances);
            }
        });
    }

    public SpecPipelineInstance create(String specDomain, String requirement) {
        synchronized (lock) {
            SpecPipelineInstance instance = new SpecPipelineInstance(specDomain, requirement);
            instances.add(instance);
            persist();
            return instance;
        }
    }

    public SpecPipelineInstance getById(String id) {
        synchronized (lock) {
            for (SpecPipelineInstance i : instances) {
                if (i.getId().equals(id)) {
                    return i;
                }
            }
            return null;
        }
    }

    public List<SpecPipelineInstance> getActive() {
        synchronized (lock) {
            List<SpecPipelineInstance> active = new ArrayList<>();
            for (SpecPipelineInstance i : instances) {
                if (i.getCurrentStage() != PipelineStage.DONE) {
                    active.add(i);
                }
            }
            return active;
        }
    }

    public List<SpecPipelineInstance> getByStage(PipelineStage stage) {
        synchronized (lock) {
            List<SpecPipelineInstance> result = new ArrayList<>();
            for (SpecPipelineInstance i : instances) {
                if (i.getCurrentStage() == stage) {
                    result.add(i);
                }
            }
            return result;
        }
    }

    public int addPlanStep(String id, String step) {
        SpecPipelineInstance i = getById(id);
        if (i != null) {
            int idx = i.addPlanStep(step);
            persist();
            return idx;
        }
        return -1;
    }

    public boolean startStepTdd(String id, int stepIndex) {
        SpecPipelineInstance i = getById(id);
        if (i != null && i.startStepTdd(stepIndex)) {
            persist();
            return true;
        }
        return false;
    }

    public boolean completeStepTdd(String id, int stepIndex) {
        SpecPipelineInstance i = getById(id);
        if (i != null && i.completeStepTdd(stepIndex)) {
            persist();
            return true;
        }
        return false;
    }

    public void linkPlan(String id, String planFile) {
        SpecPipelineInstance i = getById(id);
        if (i != null) {
            i.setPlanFile(planFile);
            persist();
        }
    }

    public void linkChange(String id, String changeName) {
        SpecPipelineInstance i = getById(id);
        if (i != null) {
            i.setChangeName(changeName);
            persist();
        }
    }

    public void advance(String id, PipelineStage stage, String note) {
        SpecPipelineInstance i = getById(id);
        if (i != null) {
            i.setStage(stage, note);
            persist();
        }
    }

    public void log(String id, String message) {
        SpecPipelineInstance i = getById(id);
        if (i != null) {
            i.log(message);
            persist();
        }
    }

    public List<SpecPipelineInstance> getInstances() {
        synchronized (lock) {
            return new ArrayList<>(instances);
        }
    }

    public int size() {
        synchronized (lock) {
            return instances.size();
        }
    }

    private void persist() {
        persistence.save(instances);
    }

    public String formatSummary() {
        synchronized (lock) {
            StringBuilder sb = new StringBuilder();
            sb.append("=== SPEC-DRIVEN PIPELINE ===\n");
            if (instances.isEmpty()) {
                sb.append("No pipeline instances. Use pipeline.start to begin.\n");
                return sb.toString();
            }
            for (SpecPipelineInstance i : instances) {
                sb.append("- [").append(i.getCurrentStage()).append("] ").append(i.getRequirement());
                if (i.getSpecDomain() != null && !i.getSpecDomain().isEmpty()) {
                    sb.append(" (spec: ").append(i.getSpecDomain()).append(")");
                }
                sb.append("\n");
            }
            return sb.toString();
        }
    }

    public String formatDetail(String id) {
        SpecPipelineInstance i = getById(id);
        if (i == null) return "Pipeline instance not found: " + id;
        StringBuilder sb = new StringBuilder();
        sb.append("=== PIPELINE INSTANCE ===\n");
        sb.append("ID: ").append(i.getId()).append("\n");
        sb.append("Stage: ").append(i.getCurrentStage()).append("\n");
        sb.append("Requirement: ").append(i.getRequirement()).append("\n");
        if (i.getSpecDomain() != null) sb.append("Spec: ").append(i.getSpecDomain()).append("\n");
        if (i.getPlanFile() != null) sb.append("Plan: ").append(i.getPlanFile()).append("\n");
        sb.append("Plan steps (").append(i.getPlanSteps().size()).append("):\n");
        for (int step = 0; step < i.getPlanSteps().size(); step++) {
            String marker = (i.getCurrentStepIndex() > step) ? "[X]"
                    : (i.getCurrentStepIndex() == step) ? "[>]"
                    : "[ ]";
            sb.append("  ").append(marker).append(" ").append(step + 1).append(". ").append(i.getPlanSteps().get(step)).append("\n");
        }
        if (i.getCurrentStepIndex() >= 0) {
            sb.append("Current step: ").append(i.getCurrentStepIndex() + 1).append("\n");
        }
        if (i.getChangeName() != null) sb.append("Change: ").append(i.getChangeName()).append("\n");
        sb.append("\n--- Stage Log ---\n");
        for (String log : i.getStageLog()) {
            sb.append(log).append("\n");
        }
        return sb.toString();
    }
}
