# Tasks

- [x] 1. RED: write failing tests for Markdown bubble rendering and streaming accumulation (`MarkdownRendererTest`, `ChatPanelTest`)
- [x] 2. Implement hand-written `MarkdownRenderer` (heading / bold / italic / inline code / code block / paragraph), no new dependencies
- [x] 3. Wire bubbles in `ChatPanel`: `renderBubbleHtml` + `appendStreamingAnswer`
- [x] 4. Convert answer area `JTextArea` -> `JTextPane`, `setContentType("text/html")`, streaming buffer field + reset
- [x] 5. Re-render on every streaming fragment via `appendStreamingChunk`; error paths re-render buffered text with the error appended
- [x] 6. Deterministic Execute Step: `AgentExecutor` required-edit tracking + auto-apply fallback (stall threshold 2, max 3 attempts)
- [x] 7. Persist TDD state (`TddEnforcerStorage`, `TddStateSnapshot`) and make PipelinePanel edit anchors idempotent
- [x] 8. Full suite green: 424 tests, 0 failures (`./gradlew test`)
- [x] 9. Commit and push the feature
