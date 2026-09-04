# Spec-Driven Pipeline (SDD -> PLAN -> TDD -> Change)

Status: COMPLETED (2026-08-28)

## Objective

Unify the SDD and TDD workflow into a single, top-down, refined pipeline where one
requirement flows through distinct stages in the correct order:

    SPEC -> PLAN -> TDD -> CHANGE -> DONE

Each plan step is executed as exactly one TDD cycle (RED-GREEN-REFACTOR), and the
change is created at the end. This corrects the earlier disconnected tool families:
previously SDD and TDD were two parallel, unrelated tool sets with no shared state,
verification handoff, or single progress ledger.

## Why PLAN before TDD

- PLAN splits the requirement into implementable steps; TDD needs a step-level task
  boundary to run a RED-GREEN-REFACTOR cycle (one step = one cycle).
- The generated plan is already TDD-format (test-first per step), so it naturally
  feeds the TDD enforcer.
- Change is landed after all steps are implemented and test-verified.

## Pipeline Flow (correct order)

1. SPEC   - requirement captured in OpenSpec (read_spec / create_change).
2. PLAN   - add plan steps (add_step) + generate implementation plan (advance_plan).
3. TDD    - for each plan step, run a TDD cycle (start_tdd -> tdd_enforcer ->
            complete_tdd). When all steps done, advance to CHANGE.
4. CHANGE - create the change, implement, archive to merge (link_change -> done).
5. DONE   - requirement fully delivered.

## Components

### Pipeline Model (`agent/pipeline/`)
- `PipelineStage` - enum: SPEC, PLAN, TDD, CHANGE, DONE
- `SpecPipelineInstance` - id, specDomain, requirement, currentStage, planFile,
  changeName, planSteps[], currentStepIndex, stage log.
- `PipelineStorageService` - JSON persistence to `.idea/myplugin-pipeline.json`.
- `PipelineService` - core CRUD + plan steps + step-level TDD tracking + linking +
  summary/detail; pluggable `Persistence` for in-memory unit tests.

### Orchestrator
- `SDDPipelineTool` (name `sdd_pipeline`) - actions: start, add_step, advance_plan,
  start_tdd, complete_tdd, link_change, complete, status, list, next_step.

### TDD Integration
- `TddEnforcerTool` gains `link_pipeline` action and optional `pipeline_id`.
- When linked and the pipeline is in TDD stage with an active step, completing
  `mark_refactor` (REFACTOR -> DONE) marks that plan step complete; when all steps
  are done, advances the pipeline to CHANGE.
- Wired via a two-arg constructor taking a `PipelineService`.

## Tests

- PipelineServiceTest
- SDDPipelineToolTest
- TddEnforcerPipelineIntegrationTest (verifies TDD DONE advances pipeline)

## Result

All tests pass: 294 total, 0 failures, 0 errors, no regression.
