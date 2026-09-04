package com.example.myplugin.agent.memory.systemmemory;

import com.example.myplugin.agent.memory.AgentMemoryService;
import com.example.myplugin.agent.memory.MemoryCategory;

import java.util.List;

public class ProjectKnowledgeMemory {

    private final AgentMemoryService base;

    public ProjectKnowledgeMemory(AgentMemoryService base) {
        this.base = base;
    }

    public void recordTechStack(String tech) {
        base.addEntry(MemoryCategory.TECH_STACK, tech, "project-knowledge", 0.95);
    }

    public void recordConvention(String convention) {
        base.addEntry(MemoryCategory.CONVENTION, convention, "project-knowledge", 0.9);
    }

    public void recordDecision(String subject, String decision) {
        base.addEntry(MemoryCategory.DECISION, subject + ": " + decision, "project-knowledge", 0.95);
    }

    public void recordPreference(String preference) {
        base.addEntry(MemoryCategory.PREFERENCE, preference, "project-knowledge", 0.85);
    }

    public void recordFact(String fact) {
        base.addEntry(MemoryCategory.FACT, fact, "project-knowledge", 0.8);
    }

    public List<com.example.myplugin.agent.memory.MemoryEntry> getTechStack() {
        return base.getByCategory(MemoryCategory.TECH_STACK);
    }

    public List<com.example.myplugin.agent.memory.MemoryEntry> getConventions() {
        return base.getByCategory(MemoryCategory.CONVENTION);
    }

    public List<com.example.myplugin.agent.memory.MemoryEntry> getDecisions() {
        return base.getByCategory(MemoryCategory.DECISION);
    }

    public List<com.example.myplugin.agent.memory.MemoryEntry> getPreferences() {
        return base.getByCategory(MemoryCategory.PREFERENCE);
    }

    public List<com.example.myplugin.agent.memory.MemoryEntry> getFacts() {
        return base.getByCategory(MemoryCategory.FACT);
    }

    public List<com.example.myplugin.agent.memory.MemoryEntry> search(String query) {
        return base.search(query);
    }

    public String getAllKnowledge() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== PROJECT KNOWLEDGE ===\n\n");

        List<com.example.myplugin.agent.memory.MemoryEntry> tech = getTechStack();
        if (!tech.isEmpty()) {
            sb.append("Tech Stack:\n");
            for (var e : tech) sb.append("- ").append(e.getContent()).append("\n");
            sb.append("\n");
        }

        List<com.example.myplugin.agent.memory.MemoryEntry> conventions = getConventions();
        if (!conventions.isEmpty()) {
            sb.append("Conventions:\n");
            for (var e : conventions) sb.append("- ").append(e.getContent()).append("\n");
            sb.append("\n");
        }

        List<com.example.myplugin.agent.memory.MemoryEntry> decisions = getDecisions();
        if (!decisions.isEmpty()) {
            sb.append("Decisions:\n");
            for (var e : decisions) sb.append("- ").append(e.getContent()).append("\n");
            sb.append("\n");
        }

        List<com.example.myplugin.agent.memory.MemoryEntry> prefs = getPreferences();
        if (!prefs.isEmpty()) {
            sb.append("Preferences:\n");
            for (var e : prefs) sb.append("- ").append(e.getContent()).append("\n");
            sb.append("\n");
        }

        return sb.toString();
    }
}
