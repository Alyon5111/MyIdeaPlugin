package com.example.myplugin.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Conversation {

    public enum Role {
        USER, ASSISTANT
    }

    public static class ChatMessage {
        private final Role role;
        private final String content;
        private String thinking;

        public ChatMessage(Role role, String content) {
            this.role = role;
            this.content = content;
            this.thinking = "";
        }

        public Role getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }

        public String getThinking() {
            return thinking;
        }

        public void setThinking(String thinking) {
            this.thinking = thinking;
        }
    }

    private final String id;
    private String title;
    private final List<ChatMessage> messages;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Conversation(String title) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.messages = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void addMessage(Role role, String content) {
        messages.add(new ChatMessage(role, content));
        updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
