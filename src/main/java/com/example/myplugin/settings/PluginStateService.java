package com.example.myplugin.settings;

import com.example.myplugin.model.Constant;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
        name = "MyPluginSettings",
        storages = @Storage("MyPluginSettings.xml")
)
public class PluginStateService implements PersistentStateComponent<PluginStateService.State> {

    public static class State {
        public String llamaCppUrl = Constant.LLAMA_CPP_MODEL_URL;
        public String apiKey = "";
        public double temperature = Constant.TEMPERATURE;
        public double topP = Constant.TOP_P;
        public int maxTokens = Constant.MAX_OUTPUT_TOKENS;
        public int maxRetries = Constant.MAX_RETRIES;
        public int timeout = Constant.TIMEOUT;
        public int agentMaxIterations = Constant.AGENT_MAX_ITERATIONS;
        public int agentMaxToolCalls = Constant.AGENT_MAX_TOOL_CALLS;
        public int agentMaxHistory = Constant.AGENT_MAX_HISTORY;
        public int agentMaxContextMessages = Constant.AGENT_MAX_CONTEXT_MESSAGES;
        public boolean streamMode = true;
        public boolean memoryEnabled = Constant.MEMORY_ENABLED;
        public boolean memoryExtractionEnabled = Constant.MEMORY_EXTRACTION_ENABLED;
        public int memoryMaxInjection = Constant.MEMORY_MAX_INJECTION;
        public boolean memoryAutoPrune = Constant.MEMORY_AUTO_PRUNE;
        public int memoryMaxEntries = Constant.MEMORY_MAX_ENTRIES;
    }

    private State myState = new State();

    public static PluginStateService getInstance() {
        return ApplicationManager.getApplication().getService(PluginStateService.class);
    }

    @Override
    public @Nullable State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        myState = state;
    }

    public String getLlamaCppUrl() {
        return myState.llamaCppUrl;
    }

    public void setLlamaCppUrl(String url) {
        myState.llamaCppUrl = url;
    }

    public String getApiKey() {
        return myState.apiKey;
    }

    public void setApiKey(String apiKey) {
        myState.apiKey = apiKey;
    }

    public double getTemperature() {
        return myState.temperature;
    }

    public void setTemperature(double temperature) {
        myState.temperature = temperature;
    }

    public double getTopP() {
        return myState.topP;
    }

    public void setTopP(double topP) {
        myState.topP = topP;
    }

    public int getMaxTokens() {
        return myState.maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        myState.maxTokens = maxTokens;
    }

    public int getTimeout() {
        return myState.timeout;
    }

    public void setTimeout(int timeout) {
        myState.timeout = timeout;
    }

    public int getMaxRetries() {
        return myState.maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        myState.maxRetries = maxRetries;
    }

    public int getAgentMaxIterations() {
        return myState.agentMaxIterations;
    }

    public void setAgentMaxIterations(int agentMaxIterations) {
        myState.agentMaxIterations = agentMaxIterations;
    }

    public int getAgentMaxToolCalls() {
        return myState.agentMaxToolCalls;
    }

    public void setAgentMaxToolCalls(int agentMaxToolCalls) {
        myState.agentMaxToolCalls = agentMaxToolCalls;
    }

    public int getAgentMaxHistory() {
        return myState.agentMaxHistory;
    }

    public void setAgentMaxHistory(int agentMaxHistory) {
        myState.agentMaxHistory = agentMaxHistory;
    }

    public int getAgentMaxContextMessages() {
        return myState.agentMaxContextMessages;
    }

    public void setAgentMaxContextMessages(int agentMaxContextMessages) {
        myState.agentMaxContextMessages = agentMaxContextMessages;
    }

    public boolean isStreamMode() {
        return myState.streamMode;
    }

    public void setStreamMode(boolean streamMode) {
        myState.streamMode = streamMode;
    }

    public boolean isMemoryEnabled() {
        return myState.memoryEnabled;
    }

    public void setMemoryEnabled(boolean memoryEnabled) {
        myState.memoryEnabled = memoryEnabled;
    }

    public boolean isMemoryExtractionEnabled() {
        return myState.memoryExtractionEnabled;
    }

    public void setMemoryExtractionEnabled(boolean memoryExtractionEnabled) {
        myState.memoryExtractionEnabled = memoryExtractionEnabled;
    }

    public int getMemoryMaxInjection() {
        return myState.memoryMaxInjection;
    }

    public void setMemoryMaxInjection(int memoryMaxInjection) {
        myState.memoryMaxInjection = memoryMaxInjection;
    }

    public boolean isMemoryAutoPrune() {
        return myState.memoryAutoPrune;
    }

    public void setMemoryAutoPrune(boolean memoryAutoPrune) {
        myState.memoryAutoPrune = memoryAutoPrune;
    }

    public int getMemoryMaxEntries() {
        return myState.memoryMaxEntries;
    }

    public void setMemoryMaxEntries(int memoryMaxEntries) {
        myState.memoryMaxEntries = memoryMaxEntries;
    }
}
