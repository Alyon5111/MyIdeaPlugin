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
}
