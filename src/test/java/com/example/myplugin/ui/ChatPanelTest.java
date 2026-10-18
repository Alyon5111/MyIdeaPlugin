package com.example.myplugin.ui;

import com.example.myplugin.service.ConversationService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatPanelTest {

    private void flushEdt() {
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ignored) {
        }
    }

    @Test
    void stopDuringStreamingResetsUiState() {
        ChatPanel panel = new ChatPanel(null, ConversationService.inMemory());
        panel.simulateStreamingForTest();
        flushEdt();
        assertTrue(panel.isStreamingActive());
        assertTrue(panel.isStopEnabled());
        assertFalse(panel.isSendEnabled());

        panel.stopStreaming();
        flushEdt();

        assertFalse(panel.isStreamingActive());
        assertTrue(panel.isSendEnabled());
        assertFalse(panel.isStopEnabled());
    }

    @Test
    void renderBubbleHtmlPrefixesByRole() {
        assertEquals("<html><body>You: Hello</body></html>",
                ChatPanel.renderBubbleHtml("USER", "Hello"));
        assertEquals("<html><body>You: Hello</body></html>",
                ChatPanel.renderBubbleHtml("user", "Hello"));
        assertEquals("<html><body>Assistant: Hello</body></html>",
                ChatPanel.renderBubbleHtml("assistant", "Hello"));
    }

    @Test
    void renderBubbleHtmlTreatsUnknownRoleAsAssistant() {
        assertEquals("<html><body>Assistant: Hi</body></html>",
                ChatPanel.renderBubbleHtml("moderator", "Hi"));
    }

    @Test
    void renderBubbleHtmlEmptyAndNullContent() {
        assertEquals("<html><body>You: </body></html>",
                ChatPanel.renderBubbleHtml("user", null));
        assertEquals("<html><body>You: </body></html>",
                ChatPanel.renderBubbleHtml("USER", ""));
        assertEquals("<html><body>Assistant: </body></html>",
                ChatPanel.renderBubbleHtml("assistant", ""));
    }

    @Test
    void renderBubbleHtmlRendersBold() {
        assertTrue(ChatPanel.renderBubbleHtml("assistant", "**bold**")
                .contains("<strong>bold</strong>"));
    }

    @Test
    void renderBubbleHtmlWrapsParagraphs() {
        String html = ChatPanel.renderBubbleHtml("user", "First\n\nSecond");
        assertTrue(html.contains("<p>First</p>"));
        assertTrue(html.contains("<p>Second</p>"));
    }

    @Test
    void renderStreamingAnswerRerendersAccumulatedText() {
        assertEquals("<html><body>Assistant: Hello</body></html>",
                ChatPanel.renderStreamingAnswer("Hello"));
        assertTrue(ChatPanel.renderStreamingAnswer("**bold**")
                .contains("<strong>bold</strong>"));
        String html = ChatPanel.renderStreamingAnswer("First\n\nSecond");
        assertTrue(html.contains("<p>First</p>"));
        assertTrue(html.contains("<p>Second</p>"));
    }

    @Test
    void renderStreamingAnswerEmptyAndNull() {
        assertEquals("<html><body>Assistant: </body></html>",
                ChatPanel.renderStreamingAnswer(null));
        assertEquals("<html><body>Assistant: </body></html>",
                ChatPanel.renderStreamingAnswer(""));
    }

    @Test
    void appendStreamingChunkAccumulatesAndRendersEachFragment() {
        StringBuilder buffer = new StringBuilder();
        assertEquals("<html><body>Assistant: Hel</body></html>",
                ChatPanel.appendStreamingChunk("Hel", buffer));
        assertEquals("<html><body>Assistant: Hello</body></html>",
                ChatPanel.appendStreamingChunk("lo", buffer));
        assertEquals("<html><body>Assistant: Hello</body></html>",
                ChatPanel.appendStreamingChunk(null, buffer));
    }

    @Test
    void appendStreamingChunkRendersMarkdownAcrossFragments() {
        StringBuilder buffer = new StringBuilder();
        ChatPanel.appendStreamingChunk("**bo", buffer);
        assertTrue(ChatPanel.appendStreamingChunk("ld**", buffer)
                .contains("<strong>bold</strong>"));
    }
}
