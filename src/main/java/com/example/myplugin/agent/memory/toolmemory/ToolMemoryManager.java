package com.example.myplugin.agent.memory.toolmemory;

import com.example.myplugin.agent.memory.AgentMemoryService;
import com.example.myplugin.agent.memory.MemoryCategory;

import java.util.HashMap;
import java.util.Map;

public class ToolMemoryManager {

    private final AgentMemoryService base;
    private final Map<String, String> runtimeStates = new HashMap<>();

    public ToolMemoryManager(AgentMemoryService base) {
        this.base = base;
    }

    public void rememberToolState(String toolName, String state) {
        base.addEntry(MemoryCategory.TOOL_STATE, toolName + ": " + state, "tool-memory", 0.7);
        runtimeStates.put(toolName, state);
    }

    public String getToolState(String toolName) {
        String runtime = runtimeStates.get(toolName);
        if (runtime != null) return runtime;
        return base.search(toolName).stream()
                .filter(e -> e.getCategory() == MemoryCategory.TOOL_STATE)
                .map(com.example.myplugin.agent.memory.MemoryEntry::getContent)
                .findFirst()
                .orElse("");
    }

    public Map<String, String> getAllToolStates() {
        return new HashMap<>(runtimeStates);
    }
}
