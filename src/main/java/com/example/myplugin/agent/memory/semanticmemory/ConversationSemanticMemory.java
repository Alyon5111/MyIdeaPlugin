package com.example.myplugin.agent.memory.semanticmemory;

import com.example.myplugin.agent.memory.AgentMemoryService;
import com.example.myplugin.agent.memory.MemoryCategory;
import com.example.myplugin.model.Conversation;

import java.util.List;

public class ConversationSemanticMemory {

    private final AgentMemoryService base;
    private final ConversationMemoryExtractor extractor;

    public ConversationSemanticMemory(AgentMemoryService base) {
        this.base = base;
        this.extractor = new ConversationMemoryExtractor();
    }

    public AgentMemoryService getBase() {
        return base;
    }

    public ConversationMemoryExtractor getExtractor() {
        return extractor;
    }

    public int extractAndStore(Conversation conversation) {
        List<ConversationMemoryExtractor.ExtractedFact> facts =
                extractor.extractFromConversation(conversation);
        int stored = 0;
        for (ConversationMemoryExtractor.ExtractedFact fact : facts) {
            base.addEntry(fact.getCategory(), fact.getContent(), "extracted: " + conversation.getId(), fact.getConfidence());
            stored++;
        }
        return stored;
    }

    public void record(String content, MemoryCategory category, String source) {
        base.addEntry(category, content, source, 0.75);
    }

    public String search(String query) {
        List<com.example.myplugin.agent.memory.MemoryEntry> results = base.search(query);
        if (results.isEmpty()) {
            return "No semantic memory matches: " + query;
        }
        StringBuilder sb = new StringBuilder("=== SEMANTIC MEMORY RESULTS ===\n");
        for (com.example.myplugin.agent.memory.MemoryEntry e : results) {
            sb.append("- [").append(e.getCategory()).append("] ").append(e.getContent())
              .append(" (conf ").append(String.format("%.2f", e.getConfidence())).append(")\n");
        }
        return sb.toString();
    }
}
