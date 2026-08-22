package com.example.myplugin.settings;

import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

public class SettingsConfigurable implements Configurable {

    private SettingsComponent settingsComponent;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "My Plugin Settings";
    }

    @Override
    public @Nullable JComponent createComponent() {
        settingsComponent = new SettingsComponent();
        return settingsComponent.getPanel();
    }

    @Override
    public boolean isModified() {
        PluginStateService state = PluginStateService.getInstance();
        return !settingsComponent.getUrlText().equals(state.getLlamaCppUrl())
                || !settingsComponent.getApiKeyText().equals(state.getApiKey())
                || settingsComponent.getTemperature() != state.getTemperature()
                || settingsComponent.getTopP() != state.getTopP()
                || settingsComponent.getMaxTokens() != state.getMaxTokens()
                || settingsComponent.getTimeout() != state.getTimeout()
                || settingsComponent.isStreamMode() != state.isStreamMode();
    }

    @Override
    public void apply() {
        PluginStateService state = PluginStateService.getInstance();
        state.setLlamaCppUrl(settingsComponent.getUrlText());
        state.setApiKey(settingsComponent.getApiKeyText());
        state.setTemperature(settingsComponent.getTemperature());
        state.setTopP(settingsComponent.getTopP());
        state.setMaxTokens(settingsComponent.getMaxTokens());
        state.setTimeout(settingsComponent.getTimeout());
        state.setStreamMode(settingsComponent.isStreamMode());
    }

    @Override
    public void reset() {
        PluginStateService state = PluginStateService.getInstance();
        settingsComponent.setUrlText(state.getLlamaCppUrl());
        settingsComponent.setApiKeyText(state.getApiKey());
        settingsComponent.setTemperature(state.getTemperature());
        settingsComponent.setTopP(state.getTopP());
        settingsComponent.setMaxTokens(state.getMaxTokens());
        settingsComponent.setTimeout(state.getTimeout());
        settingsComponent.setStreamMode(state.isStreamMode());
    }

    @Override
    public void disposeUIResources() {
        settingsComponent = null;
    }
}
