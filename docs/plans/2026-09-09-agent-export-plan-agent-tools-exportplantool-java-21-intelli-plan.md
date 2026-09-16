# 新增 Agent 工具 export_plan：在 agent-tools 模块中新增 Export Implementation Plan

> **For agentic workers:** Use TDD workflow for each step.

**Goal:** 新增 Agent 工具 export_plan：在 agent-tools 模块中新增 ExportPlanTool（Java 21，IntelliJ Platform SDK，LangChain4j）。工具读取 docs/plans 目录下的所有 .md 计划文档，解析每个文档的任务/步骤清单，汇总生成一份 Markdown 导出报告，写入 docs/plans/export/ 目录（文件名带时间戳，如 export-YYYYMMDD-HHmmss.md）。包含单元测试（JUnit 5）：验证目录扫描、Markdown 内容解析、导出文件生成。文件：src/main/java/com/example/agent/tools/ExportPlanTool.java、src/test/java/com/example/agent/tools/ExportPlanToolTest.java

**Architecture:** To be determined during implementation.

**Tech Stack:** Java 21, IntelliJ Platform SDK, LangChain4j

## File Structure

| File | Action | Purpose |
|------|--------|---------|
| TBD | CREATE | As needed |

## Task 1: 新增 Agent 工具 export_plan：在 agent-tools 模块中新增 Export

**Files:** TBD

- [ ] Step 1: Write failing test
  Run: `./gradlew test`
  Expected: BUILD FAILED

```java
// Test code
```

- [ ] Step 2: Implement feature
  Run: `./gradlew test`
  Expected: BUILD SUCCESSFUL

```java
// Implementation
```

- [ ] Step 3: Commit
  Run: `git add -A && git commit -m "feat: 新增-agent-工具-export_plan：在-agent-tools-模块中新增-export"`

