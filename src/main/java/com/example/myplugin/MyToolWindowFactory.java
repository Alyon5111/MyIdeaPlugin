package com.example.myplugin;

import com.example.myplugin.model.Conversation;
import com.example.myplugin.service.ConversationService;
import com.example.myplugin.ui.ChatPanel;
import com.example.myplugin.ui.SettingsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public class MyToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ConversationService service = project.getService(ConversationService.class);

        ChatPanel chatPanel = new ChatPanel(project, service);
        SettingsPanel settingsPanel = new SettingsPanel();

        service.setOnConversationChanged(chatPanel::loadConversation);

        if (service.getConversations().isEmpty()) {
            Conversation firstConv = service.createNewConversation(null);
            chatPanel.loadConversation(firstConv);
        } else {
            for (int i = service.getConversations().size() - 1; i >= 0; i--) {
                chatPanel.loadConversation(service.getConversations().get(i));
            }
            Conversation current = service.getCurrentConversation();
            if (current != null) {
                chatPanel.loadConversation(current);
            }
        }

        ContentFactory contentFactory = ContentFactory.getInstance();

        Content chatContent = contentFactory.createContent(chatPanel, "Chat", false);
        chatContent.setCloseable(false);
        toolWindow.getContentManager().addContent(chatContent);

        Content settingsContent = contentFactory.createContent(settingsPanel, "Settings", false);
        settingsContent.setCloseable(false);
        toolWindow.getContentManager().addContent(settingsContent);
    }
}
