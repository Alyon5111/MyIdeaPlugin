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
        public int timeout = Constant.TIMEOUT;
        public boolean streamMode = true;
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

    public boolean isStreamMode() {
        return myState.streamMode;
    }

    public void setStreamMode(boolean streamMode) {
        myState.streamMode = streamMode;
    }
}
