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
        private String content;
        private String thinking;
        private final List<ToolCallRecord> toolCalls = new ArrayList<>();

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

        public void setContent(String content) {
            this.content = content;
        }

        public String getThinking() {
            return thinking;
        }

        public void setThinking(String thinking) {
            this.thinking = thinking;
        }

        public List<ToolCallRecord> getToolCalls() {
            return toolCalls;
        }

        public void addToolCall(String toolName, String arguments, String result) {
            toolCalls.add(new ToolCallRecord(toolName, arguments, result));
        }

        public void updateLastToolCallResult(String result) {
            if (!toolCalls.isEmpty()) {
                ToolCallRecord last = toolCalls.get(toolCalls.size() - 1);
                toolCalls.set(toolCalls.size() - 1,
                        new ToolCallRecord(last.getToolName(), last.getArguments(), result));
            }
        }
    }

    public static class ToolCallRecord {
        private final String toolName;
        private final String arguments;
        private final String result;

        public ToolCallRecord(String toolName, String arguments, String result) {
            this.toolName = toolName;
            this.arguments = arguments;
            this.result = result;
        }

        public String getToolName() { return toolName; }
        public String getArguments() { return arguments; }
        public String getResult() { return result; }
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
