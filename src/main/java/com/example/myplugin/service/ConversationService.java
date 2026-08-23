package com.example.myplugin.service;

import com.example.myplugin.model.Conversation;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ConversationService {

    private final Project project;
    private final List<Conversation> conversations;
    private Conversation currentConversation;
    private Consumer<Conversation> onConversationChanged;
    private int conversationCounter = 0;

    public ConversationService(@NotNull Project project) {
        this.project = project;
        conversations = new ArrayList<>();
        loadFromDisk();
    }

    private void loadFromDisk() {
        List<Conversation> saved = ConversationStorageService.load(project);
        conversations.addAll(saved);
        conversationCounter = saved.size();
        if (!conversations.isEmpty()) {
            currentConversation = conversations.get(0);
        }
    }

    public void reset() {
        conversations.clear();
        currentConversation = null;
        conversationCounter = 0;
    }

    public Conversation createNewConversation(String title) {
        if (title == null || title.isEmpty()) {
            title = "New Chat " + (++conversationCounter);
        }
        Conversation conv = new Conversation(title);
        conversations.add(0, conv);
        currentConversation = conv;
        save();
        if (onConversationChanged != null) {
            onConversationChanged.accept(currentConversation);
        }
        return conv;
    }

    public void deleteConversation(Conversation conv) {
        conversations.remove(conv);
        save();
        if (currentConversation == conv) {
            if (conversations.isEmpty()) {
                createNewConversation(null);
            } else {
                switchTo(conversations.get(0));
            }
        }
    }

    public void switchTo(Conversation conv) {
        if (conv == null || conv == currentConversation) {
            return;
        }
        currentConversation = conv;
        if (onConversationChanged != null) {
            onConversationChanged.accept(currentConversation);
        }
    }

    public Conversation getCurrentConversation() {
        return currentConversation;
    }

    public Conversation findConversationById(String id) {
        for (Conversation conv : conversations) {
            if (conv.getId().equals(id)) {
                return conv;
            }
        }
        return null;
    }

    public List<Conversation> getConversations() {
        return conversations;
    }

    public int size() {
        return conversations.size();
    }

    public void setOnConversationChanged(Consumer<Conversation> listener) {
        this.onConversationChanged = listener;
    }

    public void save() {
        ConversationStorageService.save(project, conversations);
    }
}
