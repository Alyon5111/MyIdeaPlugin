package com.example.myplugin.agent.memory.semanticmemory;

import com.example.myplugin.agent.memory.AgentMemoryService;
import com.example.myplugin.model.Conversation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConversationSemanticMemoryTest {

    private AgentMemoryService newMemory() {
        return AgentMemoryService.inMemory();
    }

    @Test
    void testRuleBasedExtractionFindsKeywordFacts() {
        AgentMemoryService memory = newMemory();
        Conversation conv = new Conversation("test");
        conv.addMessage(Conversation.Role.USER, "What stack does this project use?");
        conv.addMessage(Conversation.Role.ASSISTANT, "The project uses Spring Boot and PostgreSQL.");

        ConversationSemanticMemory semantic = new ConversationSemanticMemory(memory);
        int stored = semantic.extractAndStore(conv);

        assertTrue(stored > 0);
        assertTrue(memory.size() > 0);
    }

    @Test
    void testRuleBasedExtractionFindsDecision() {
        AgentMemoryService memory = newMemory();
        Conversation conv = new Conversation("test");
        conv.addMessage(Conversation.Role.USER, "We decided to use Gradle instead of Maven.");

        ConversationSemanticMemory semantic = new ConversationSemanticMemory(memory);
        semantic.extractAndStore(conv);

        List<com.example.myplugin.agent.memory.MemoryEntry> decisions =
                memory.getByCategory(com.example.myplugin.agent.memory.MemoryCategory.DECISION);
        assertFalse(decisions.isEmpty());
        assertTrue(decisions.get(0).getContent().toLowerCase().contains("gradle"));
    }

    @Test
    void testRecordSemanticMemoryAndSearch() {
        AgentMemoryService memory = newMemory();
        ConversationSemanticMemory semantic = new ConversationSemanticMemory(memory);

        semantic.record("User prefers spaces over tabs", com.example.myplugin.agent.memory.MemoryCategory.PREFERENCE, "test");

        String results = semantic.search("spaces");
        assertTrue(results.contains("spaces"));
    }

    @Test
    void testSearchNoMatch() {
        AgentMemoryService memory = newMemory();
        ConversationSemanticMemory semantic = new ConversationSemanticMemory(memory);

        String results = semantic.search("nonexistenttermxyz");
        assertTrue(results.contains("No semantic memory matches"));
    }
}
