package com.example.myplugin.agent.pipeline;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SpecPipelineInstance {

    private String id;
    private String specDomain;
    private String requirement;
    private PipelineStage currentStage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String planFile;
    private String changeName;

    private List<String> planSteps = new ArrayList<>();
    private int currentStepIndex = -1;

    private List<String> stageLog;

    private SpecPipelineInstance() {}

    public SpecPipelineInstance(String specDomain, String requirement) {
        this.id = UUID.randomUUID().toString();
        this.specDomain = specDomain;
        this.requirement = requirement;
        this.currentStage = PipelineStage.SPEC;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        this.stageLog = new ArrayList<>();
        this.stageLog.add("CREATED at SPEC stage for requirement: " + requirement);
    }

    public String getId() { return id; }
    public String getSpecDomain() { return specDomain; }
    public String getRequirement() { return requirement; }
    public PipelineStage getCurrentStage() { return currentStage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getPlanFile() { return planFile; }
    public String getChangeName() { return changeName; }
    public List<String> getPlanSteps() { return planSteps; }
    public int getCurrentStepIndex() { return currentStepIndex; }
    public List<String> getStageLog() { return stageLog; }

    public void setSpecDomain(String specDomain) { this.specDomain = specDomain; }
    public void setPlanFile(String planFile) { this.planFile = planFile; }
    public void setChangeName(String changeName) { this.changeName = changeName; }

    public void setStage(PipelineStage stage, String note) {
        this.currentStage = stage;
        this.updatedAt = LocalDateTime.now();
        log("ADVANCED to " + stage.name() + (note != null && !note.isEmpty() ? ": " + note : ""));
    }

    public int addPlanStep(String step) {
        planSteps.add(step);
        return planSteps.size() - 1;
    }

    public boolean startStepTdd(int stepIndex) {
        if (stepIndex < 0 || stepIndex >= planSteps.size()) {
            return false;
        }
        this.currentStepIndex = stepIndex;
        this.updatedAt = LocalDateTime.now();
        log("STARTED TDD for step " + (stepIndex + 1) + ": " + planSteps.get(stepIndex));
        return true;
    }

    public boolean completeStepTdd(int stepIndex) {
        if (this.currentStepIndex != stepIndex) {
            return false;
        }
        this.updatedAt = LocalDateTime.now();
        log("TDD DONE for step " + (stepIndex + 1) + ": " + planSteps.get(stepIndex));
        return true;
    }

    public boolean allStepsDone() {
        return currentStepIndex == planSteps.size() - 1 && planSteps.size() > 0;
    }

    public String currentStepTddTask() {
        if (currentStepIndex >= 0 && currentStepIndex < planSteps.size()) {
            return planSteps.get(currentStepIndex);
        }
        return null;
    }

    public void log(String message) {
        stageLog.add("[" + LocalDateTime.now().withNano(0) + "] " + message);
    }
}
