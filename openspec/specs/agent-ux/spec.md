# Agent Ux

## Purpose

Spec-driven development starts here. This spec captures the requirement that flows
through the pipeline SPEC -> PLAN -> TDD -> Change -> DONE.

## Requirements

### Requirement: Optimize the Agent pipeline execution flow and interaction. 1. Add a Pipeline operation panel: pipeline start, add_step, generate plan, advance_plan, start_tdd, complete, create_change, link_change, export_plan and other commands are all converted to UI buttons and form operations, directly invoking existing tools instead of relying on the model to generate parameters. 2. The Agent on/off switch is persisted across restarts into the plugin configuration. 3. Tool invocations are visualized in the chat panel as collapsible cards showing the tool name and parameters; thinking blocks keep only the reasoning content. 4. Before context trimming, show a hint in the panel to avoid silently losing user constraint information. 5. The text command entry is retained, coexisting with the UI as dual channels; operations from both channels are written into the trace records of myplugin-conversations.json.

The system SHALL Optimize the Agent pipeline execution flow and interaction. 1. Add a Pipeline operation panel: pipeline start, add_step, generate plan, advance_plan, start_tdd, complete, create_change, link_change, export_plan and other commands are all converted to UI buttons and form operations, directly invoking existing tools instead of relying on the model to generate parameters. 2. The Agent on/off switch is persisted across restarts into the plugin configuration. 3. Tool invocations are visualized in the chat panel as collapsible cards showing the tool name and parameters; thinking blocks keep only the reasoning content. 4. Before context trimming, show a hint in the panel to avoid silently losing user constraint information. 5. The text command entry is retained, coexisting with the UI as dual channels; operations from both channels are written into the trace records of myplugin-conversations.json.

#### Scenario: Satisfy the requirement

- WHEN the pipeline executes the requirement
- THEN the requirement is implemented and verified via TDD

> Note (Chinese): 优化 Agent 流水线的执行流程与交互方式：Pipeline 操作面板、Agent 开关持久化、工具调用可折叠卡片、上下文裁剪提示、文本命令与 UI 双通道并存且均写入 trace 记录。
