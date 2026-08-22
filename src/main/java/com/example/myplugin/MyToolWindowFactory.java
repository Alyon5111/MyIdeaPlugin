package com.example.myplugin;

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

    private Content chatContent;

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ChatPanel chatPanel = new ChatPanel();
        SettingsPanel settingsPanel = new SettingsPanel();

        ConversationService service = ConversationService.getInstance();

        service.setOnConversationChanged(conv -> {
            chatPanel.loadConversation(conv);
        });

        service.setOnAllConversationsDeleted(() -> {
            service.createNewConversation(null);
        });

        service.createNewConversation(null);

        ContentFactory contentFactory = ContentFactory.getInstance();

        chatContent = contentFactory.createContent(chatPanel, "Chat", false);
        chatContent.setCloseable(false);
        toolWindow.getContentManager().addContent(chatContent);

        Content settingsContent = contentFactory.createContent(settingsPanel, "Settings", false);
        settingsContent.setCloseable(false);
        toolWindow.getContentManager().addContent(settingsContent);
    }
}
