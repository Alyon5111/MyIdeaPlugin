# Chat Ui

## Purpose

Spec-driven development starts here. This spec captures the requirement that flows
through the pipeline SPEC -> PLAN -> TDD -> Change -> DONE.

## Requirements
### Requirement: Implement a Stop button in the Chat panel: streaming generation / Agent responses can be terminated mid-flight by the user, and the Send/Stop/input field state is reset accordingly.

The system SHALL Implement a Stop button in the Chat panel: streaming generation / Agent responses can be terminated mid-flight by the user, and the Send/Stop/input field state is reset accordingly.

#### Scenario: Satisfy the requirement

- WHEN the pipeline executes the requirement
- THEN the requirement is implemented and verified via TDD

> Note (Chinese): Chat 面板 Stop 按钮：流式生成/Agent 应答可被用户中途终止，并复位 Send/Stop/输入框状态

### Requirement: Streaming Markdown answer rendering

The chat answer pane SHALL render accumulated assistant text as HTML derived from Markdown, using a hand-written renderer with no new third-party dependencies.

#### Scenario: Fragment accumulation and re-render

- WHEN the assistant answer streams in fragments
- THEN each fragment is appended to a buffer and the full accumulated text is re-rendered as HTML in the answer pane

#### Scenario: Markdown features inside the bubble

- WHEN the accumulated text contains headings, bold, italic, inline code, or fenced code blocks
- THEN they are rendered as styled HTML in the message bubble instead of raw Markdown syntax

#### Scenario: Error during streaming

- WHEN an error occurs while streaming
- THEN the buffered answer is re-rendered with the error appended and the Send/Stop/input field state is reset


