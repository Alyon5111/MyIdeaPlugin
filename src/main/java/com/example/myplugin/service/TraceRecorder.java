package com.example.myplugin.service;

import com.example.myplugin.model.Conversation;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Unified trace recorder. Both the text command channel and the UI (pipeline panel) channel write
 * their tool invocations here, persisted into the current conversation's trace list (saved to
 * myplugin-conversations.json). Each entry carries a channel tag: "text" or "ui".
 */
public class TraceRecorder {

    private TraceRecorder() {}

    public static void record(@NotNull Project project, String toolName, String arguments,
                              String result, String channel) {
        if (project == null || project.isDisposed()) return;
        ConversationService service = project.getService(ConversationService.class);
        Conversation conv = service != null ? service.getCurrentConversation() : null;
        if (conv == null) return;
        conv.addTrace(toolName, arguments, result, channel);
        service.save();
    }
}