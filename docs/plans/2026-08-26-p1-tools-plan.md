# P1 Tools Implementation Plan

**Date:** 2026-08-26  
**Goal:** Implement 3 new agent tools based on Superpowers methodology

---

## Tools to Implement

### 1. SystematicDebuggingTool
**Purpose:** Enforce systematic debugging process (4 phases: Root Cause → Pattern → Hypothesis → Implementation)

**Key Features:**
- State machine: ROOT_CAUSE → PATTERN → HYPOTHESIS → IMPLEMENTATION
- Red flag detection (proposing fixes without investigation)
- Track fix attempts (stop at 3+ failures, question architecture)
- Require evidence before fix proposals

**Files to Create:**
```
src/main/java/com/example/myplugin/agent/debugging/
├── SystematicDebuggingTool.java
├── DebuggingStateMachine.java
├── EvidenceCollector.java
└── RedFlagDetector.java
```

---

### 2. VerificationGateTool
**Purpose:** Prevent completion claims without verification evidence

**Key Features:**
- Gate function: IDENTIFY → RUN → READ → VERIFY → CLAIM
- Detect "should", "probably", "seems to" language
- Require fresh command output before success claims
- Track verification history

**Files to Create:**
```
src/main/java/com/example/myplugin/agent/verification/
├── VerificationGateTool.java
├── VerificationGate.java
├── ClaimDetector.java
└── EvidenceTracker.java
```

---

### 3. CodeReviewTool
**Purpose:** Systematic code review with technical evaluation

**Key Features:**
- Review checklist (security, performance, maintainability)
- Detect performative responses ("You're absolutely right!")
- YAGNI check for unused features
- Track review items (Critical/Important/Minor)

**Files to Create:**
```
src/main/java/com/example/myplugin/agent/review/
├── CodeReviewTool.java
├── ReviewChecklist.java
├── ResponseAnalyzer.java
└── IssueTracker.java
```

---

## Implementation Steps

### Phase 1: Core Infrastructure
1. Create base package structure
2. Create state machine base class (reuse from TDD)
3. Create red flag detection utilities

### Phase 2: SystematicDebuggingTool
1. Implement DebuggingStateMachine (4 phases)
2. Implement EvidenceCollector (track evidence requirements)
3. Implement RedFlagDetector (detect debugging anti-patterns)
4. Implement SystematicDebuggingTool (AgentTool interface)
5. Register in AgentExecutor

### Phase 3: VerificationGateTool
1. Implement VerificationGate (5-step gate function)
2. Implement ClaimDetector (detect success language)
3. Implement EvidenceTracker (track verification history)
4. Implement VerificationGateTool (AgentTool interface)
5. Register in AgentExecutor

### Phase 4: CodeReviewTool
1. Implement ReviewChecklist (security/perf/maintainability)
2. Implement ResponseAnalyzer (detect performative responses)
3. Implement IssueTracker (track review items)
4. Implement CodeReviewTool (AgentTool interface)
5. Register in AgentExecutor

### Phase 5: Testing
1. Unit tests for each tool
2. Integration tests with AgentExecutor
3. Verify all 113+ tests pass

---

## Success Criteria

- [ ] All 3 tools implement AgentTool interface
- [ ] All tools registered in AgentExecutor
- [ ] All tools have TOOL_KEYWORDS entries
- [ ] Unit tests for all components
- [ ] All tests pass
- [ ] No regression in existing functionality

---

## Estimated Effort

| Tool | Estimated Time |
|------|----------------|
| SystematicDebuggingTool | 2-3 hours |
| VerificationGateTool | 1-2 hours |
| CodeReviewTool | 2-3 hours |
| Testing & Integration | 1-2 hours |
| **Total** | **6-10 hours** |
