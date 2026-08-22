package com.example.myplugin.service;

import com.example.myplugin.model.Conversation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ConversationService {

    private static ConversationService instance;

    private final List<Conversation> conversations;
    private Conversation currentConversation;
    private Consumer<Conversation> onConversationChanged;
    private Runnable onAllConversationsDeleted;

    private ConversationService() {
        conversations = new ArrayList<>();
    }

    public static synchronized ConversationService getInstance() {
        if (instance == null) {
            instance = new ConversationService();
        }
        return instance;
    }

    public Conversation createNewConversation(String title) {
        if (title == null || title.isEmpty()) {
            title = "New Chat";
        }
        Conversation conv = new Conversation(title);
        conversations.add(0, conv);
        currentConversation = conv;
        if (onConversationChanged != null) {
            onConversationChanged.accept(currentConversation);
        }
        return conv;
    }

    public void deleteConversation(Conversation conv) {
        conversations.remove(conv);
        if (currentConversation == conv) {
            if (conversations.isEmpty()) {
                currentConversation = null;
                if (onAllConversationsDeleted != null) {
                    onAllConversationsDeleted.run();
                }
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

    public void setOnAllConversationsDeleted(Runnable listener) {
        this.onAllConversationsDeleted = listener;
    }
}
