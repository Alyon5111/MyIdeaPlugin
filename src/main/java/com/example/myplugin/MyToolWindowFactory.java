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
        service.reset();

        ChatPanel chatPanel = new ChatPanel(project, service);
        SettingsPanel settingsPanel = new SettingsPanel();

        Conversation firstConv = service.createNewConversation(null);

        service.setOnConversationChanged(chatPanel::loadConversation);

        chatPanel.loadConversation(firstConv);

        ContentFactory contentFactory = ContentFactory.getInstance();

        Content chatContent = contentFactory.createContent(chatPanel, "Chat", false);
        chatContent.setCloseable(false);
        toolWindow.getContentManager().addContent(chatContent);

        Content settingsContent = contentFactory.createContent(settingsPanel, "Settings", false);
        settingsContent.setCloseable(false);
        toolWindow.getContentManager().addContent(settingsContent);
    }
}
