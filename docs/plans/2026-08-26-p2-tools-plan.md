# P2 Tools Implementation Plan

**Date:** 2026-08-26  
**Goal:** Implement remaining agent tools based on Superpowers methodology

---

## Tools to Implement

### 1. ExecutingPlansTool
**Purpose:** Execute implementation plans with review checkpoints

**Key Features:**
- Load and review plan file
- Execute tasks sequentially
- Run verifications as specified
- Track task progress

**Files to Create:**
```
src/main/java/com/example/myplugin/agent/plans/
├── ExecutingPlansTool.java
├── PlanExecutor.java
└── TaskTracker.java
```

---

### 2. SubagentDrivenDevelopmentTool
**Purpose:** Execute plan by dispatching subagents per task with two-stage review

**Key Features:**
- Fresh subagent per task
- Two-stage review: spec compliance, then code quality
- Handle implementer status (DONE, NEEDS_CONTEXT, BLOCKED)
- Continuous execution without pausing

**Files to Create:**
```
src/main/java/com/example/myplugin/agent/subagent/
├── SubagentDrivenDevelopmentTool.java
├── SubagentDispatcher.java
└── ReviewCoordinator.java
```

---

### 3. GitWorkflowTool
**Purpose:** Git workflow management (worktree, branch, merge)

**Key Features:**
- Detect existing isolation (worktree)
- Create isolated workspace
- Branch management
- Merge and PR creation

**Files to Create:**
```
src/main/java/com/example/myplugin/agent/git/
├── GitWorkflowTool.java
├── WorktreeManager.java
└── BranchManager.java
```

---

### 4. FinishingBranchTool
**Purpose:** Complete development work with structured options

**Key Features:**
- Verify tests pass
- Detect environment (normal repo, worktree, detached HEAD)
- Present 4 options: Merge, PR, Keep, Discard
- Execute chosen workflow

**Files to Create:**
```
src/main/java/com/example/myplugin/agent/branch/
├── FinishingBranchTool.java
├── CompletionHandler.java
└── OptionExecutor.java
```

---

## Implementation Steps

### Phase 1: ExecutingPlansTool
1. Implement PlanExecutor (load plan, execute tasks)
2. Implement TaskTracker (track progress)
3. Implement ExecutingPlansTool (AgentTool interface)
4. Register in AgentExecutor

### Phase 2: SubagentDrivenDevelopmentTool
1. Implement SubagentDispatcher (dispatch subagents)
2. Implement ReviewCoordinator (two-stage review)
3. Implement SubagentDrivenDevelopmentTool (AgentTool interface)
4. Register in AgentExecutor

### Phase 3: GitWorkflowTool
1. Implement WorktreeManager (worktree operations)
2. Implement BranchManager (branch operations)
3. Implement GitWorkflowTool (AgentTool interface)
4. Register in AgentExecutor

### Phase 4: FinishingBranchTool
1. Implement OptionExecutor (execute merge, PR, keep, discard)
2. Implement CompletionHandler (verify tests, present options)
3. Implement FinishingBranchTool (AgentTool interface)
4. Register in AgentExecutor

### Phase 5: Testing
1. Unit tests for each tool
2. Integration tests with AgentExecutor
3. Verify all tests pass

---

## Success Criteria

- [ ] All 4 tools implement AgentTool interface
- [ ] All tools registered in AgentExecutor
- [ ] All tools have TOOL_KEYWORDS entries
- [ ] Unit tests for all components
- [ ] All tests pass
- [ ] No regression in existing functionality

---

## Estimated Effort

| Tool | Estimated Time |
|------|----------------|
| ExecutingPlansTool | 2-3 hours |
| SubagentDrivenDevelopmentTool | 3-4 hours |
| GitWorkflowTool | 2-3 hours |
| FinishingBranchTool | 2-3 hours |
| Testing & Integration | 2-3 hours |
| **Total** | **11-16 hours** |
