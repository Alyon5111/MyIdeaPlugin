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
    private List<String> planStepKinds = new ArrayList<>();
    private int currentStepIndex = -1;

    private List<String> stageLog;

    private List<Integer> tddEvidence = new ArrayList<>();

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
        return addPlanStep(step, "code");
    }

    /** Adds a plan step with an explicit kind: "code" (TDD cycle) or "verify" (regression gate). */
    public int addPlanStep(String step, String kind) {
        planSteps.add(step);
        planStepKinds.add(normalizeKind(kind));
        return planSteps.size() - 1;
    }

    private static String normalizeKind(String kind) {
        return "verify".equals(kind) ? "verify" : "code";
    }

    /** Kind of a plan step: "code" (TDD cycle) or "verify" (regression gate). Instances persisted
     *  before kinds existed have no stored kind; those default to "code". */
    public String stepKind(int stepIndex) {
        if (stepIndex >= 0 && stepIndex < planStepKinds.size()) {
            String k = planStepKinds.get(stepIndex);
            if (k != null && !k.isEmpty()) {
                return k;
            }
        }
        return "code";
    }

    public boolean isVerifyStep(int stepIndex) {
        return "verify".equals(stepKind(stepIndex));
    }

    public String currentStepKind() {
        return stepKind(currentStepIndex);
    }

    public List<String> getPlanStepKinds() { return planStepKinds; }

    public boolean setStepKind(int stepIndex, String kind) {
        if (stepIndex < 0 || stepIndex >= planSteps.size()) {
            return false;
        }
        while (planStepKinds.size() < planSteps.size()) {
            planStepKinds.add("code");
        }
        planStepKinds.set(stepIndex, normalizeKind(kind));
        this.updatedAt = LocalDateTime.now();
        log("STEP " + (stepIndex + 1) + " kind set to '" + planStepKinds.get(stepIndex) + "'");
        return true;
    }

    public boolean startStepTdd(int stepIndex) {
        if (stepIndex < 0 || stepIndex >= planSteps.size()) {
            return false;
        }
        this.currentStepIndex = stepIndex;
        this.updatedAt = LocalDateTime.now();
        String verb = isVerifyStep(stepIndex) ? "STARTED VERIFY for step " : "STARTED TDD for step ";
        log(verb + (stepIndex + 1) + ": " + planSteps.get(stepIndex));
        return true;
    }

    public boolean completeStepTdd(int stepIndex) {
        if (this.currentStepIndex != stepIndex) {
            return false;
        }
        this.updatedAt = LocalDateTime.now();
        String verb = isVerifyStep(stepIndex) ? "VERIFY DONE for step " : "TDD DONE for step ";
        log(verb + (stepIndex + 1) + ": " + planSteps.get(stepIndex));
        return true;
    }

    /** Records evidence that a step's TDD cycle really completed (via tdd_enforcer mark_refactor). */
    public void recordTddEvidence(int stepIndex) {
        if (!tddEvidence.contains(stepIndex)) {
            tddEvidence.add(stepIndex);
            log("TDD EVIDENCE for step " + (stepIndex + 1));
        }
    }

    public boolean hasTddEvidence(int stepIndex) {
        return tddEvidence.contains(stepIndex);
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
