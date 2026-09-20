package com.example.myplugin.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConversationToolCallRecordTest {

    @Test
    void addToolCallAndRetrieve() {
        Conversation conv = new Conversation("t");
        conv.addMessage(Conversation.Role.USER, "hi");
        Conversation.ChatMessage assistant = new Conversation.ChatMessage(Conversation.Role.ASSISTANT, "");
        assistant.addToolCall("read_file", "{\"path\":\"a.java\"}", "");
        conv.getMessages().add(assistant);
        assertEquals(1, assistant.getToolCalls().size());
        assertEquals("read_file", assistant.getToolCalls().get(0).getToolName());
    }

    @Test
    void updateLastToolCallResult() {
        Conversation.ChatMessage msg = new Conversation.ChatMessage(Conversation.Role.ASSISTANT, "");
        msg.addToolCall("write_file", "{}", "");
        assertEquals("", msg.getToolCalls().get(0).getResult());
        msg.updateLastToolCallResult("OK");
        assertEquals("OK", msg.getToolCalls().get(0).getResult());
    }

    @Test
    void updateLastToolCallResultOnEmptyList() {
        Conversation.ChatMessage msg = new Conversation.ChatMessage(Conversation.Role.ASSISTANT, "");
        msg.updateLastToolCallResult("noop");
        assertTrue(msg.getToolCalls().isEmpty());
    }

    @Test
    void addToolCallWithChannel() {
        Conversation.ChatMessage msg = new Conversation.ChatMessage(Conversation.Role.ASSISTANT, "");
        msg.addToolCall("sdd_pipeline", "{}", "", "ui");
        assertEquals("ui", msg.getToolCalls().get(0).getChannel());
        msg.addToolCall("read_file", "{}", "");
        assertEquals("text", msg.getToolCalls().get(1).getChannel());
    }

    @Test
    void updateLastToolCallResultPreservesChannel() {
        Conversation.ChatMessage msg = new Conversation.ChatMessage(Conversation.Role.ASSISTANT, "");
        msg.addToolCall("sdd_pipeline", "{}", "", "ui");
        msg.updateLastToolCallResult("DONE");
        Conversation.ToolCallRecord rec = msg.getToolCalls().get(0);
        assertEquals("DONE", rec.getResult());
        assertEquals("ui", rec.getChannel());
    }

    @Test
    void addTraceEntry() {
        Conversation conv = new Conversation("t");
        conv.addTrace("sdd_pipeline", "{\"action\":\"list\"}", "No active pipelines", "ui");
        conv.addTrace("tdd_enforcer", "{}", "cycle started", "text");
        assertEquals(2, conv.getTrace().size());
        Conversation.TraceEntry first = conv.getTrace().get(0);
        assertEquals("sdd_pipeline", first.getToolName());
        assertEquals("ui", first.getChannel());
        assertEquals("No active pipelines", first.getResult());
        assertNotNull(first.getTimestamp());
        first.setResult("updated");
        assertEquals("updated", conv.getTrace().get(0).getResult());
    }
}