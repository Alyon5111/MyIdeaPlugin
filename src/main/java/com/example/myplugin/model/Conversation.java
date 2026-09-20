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
        private Role role;
        private String content;
        private String thinking;
        private List<ToolCallRecord> toolCalls = new ArrayList<>();

        private ChatMessage() {}

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
            addToolCall(toolName, arguments, result, "text");
        }

        public void addToolCall(String toolName, String arguments, String result, String channel) {
            toolCalls.add(new ToolCallRecord(toolName, arguments, result, channel));
        }

        public void updateLastToolCallResult(String result) {
            if (!toolCalls.isEmpty()) {
                ToolCallRecord last = toolCalls.get(toolCalls.size() - 1);
                toolCalls.set(toolCalls.size() - 1,
                        new ToolCallRecord(last.getToolName(), last.getArguments(), result, last.getChannel()));
            }
        }
    }

    public static class ToolCallRecord {
        private String toolName;
        private String arguments;
        private String result;
        private String channel;

        private ToolCallRecord() {}

        public ToolCallRecord(String toolName, String arguments, String result) {
            this(toolName, arguments, result, "text");
        }

        public ToolCallRecord(String toolName, String arguments, String result, String channel) {
            this.toolName = toolName;
            this.arguments = arguments;
            this.result = result;
            this.channel = channel;
        }

        public String getToolName() { return toolName; }
        public String getArguments() { return arguments; }
        public String getResult() { return result; }
        public String getChannel() { return channel; }
    }

    public static class TraceEntry {
        private String toolName;
        private String arguments;
        private String result;
        private String channel;
        private LocalDateTime timestamp;

        private TraceEntry() {}

        public TraceEntry(String toolName, String arguments, String result, String channel) {
            this.toolName = toolName;
            this.arguments = arguments;
            this.result = result;
            this.channel = channel;
            this.timestamp = LocalDateTime.now();
        }

        public String getToolName() { return toolName; }
        public String getArguments() { return arguments; }
        public String getResult() { return result; }
        public String getChannel() { return channel; }
        public LocalDateTime getTimestamp() { return timestamp; }

        public void setResult(String result) { this.result = result; }
    }

    private final String id;
    private String title;
    private final List<ChatMessage> messages;
    private final List<TraceEntry> trace = new ArrayList<>();
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

    public List<TraceEntry> getTrace() {
        return trace;
    }

    public void addTrace(String toolName, String arguments, String result, String channel) {
        trace.add(new TraceEntry(toolName, arguments, result, channel));
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
