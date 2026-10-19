# chat-jtextpane-streaming

## Why

Chat answers were shown as plain text in a `JTextArea`, so Markdown produced by the model (headings, bold, italic, inline code, code blocks) reached the user as raw `**` / `#` syntax. Streaming appended fragments to the area without ever re-rendering, so partial Markdown stayed broken.

## What Changes

- Replace the answer area `JTextArea` with a `JTextPane` (`text/html`) and re-render the accumulated buffer on every streaming fragment.
- Add a hand-written `MarkdownRenderer` (no new third-party dependencies) and wrap its HTML into chat bubbles via `renderBubbleHtml` / `appendStreamingAnswer`.
- Error paths re-render the buffered answer with the error appended instead of replacing it with plain text.
- Supporting work: make Execute Step deterministic (`AgentExecutor` required-edit tracking with auto-apply fallback), persist TDD state (`TddEnforcerStorage`, `TddStateSnapshot`) and make PipelinePanel edit anchors idempotent so repeat cycles are reproducible.

## Impact

- Affected specs: chat-ui
- Code: `ui/ChatPanel.java`, `markdown/MarkdownRenderer.java`, `agent/AgentExecutor.java`, `ui/PipelinePanel.java`, `agent/tdd/*`
- Tests: `MarkdownRendererTest`, `ChatPanelTest`, `AgentExecutorTddProgressTest` (full suite: 424 green)
