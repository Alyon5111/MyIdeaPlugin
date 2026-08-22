package com.example.myplugin.settings;

import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SettingsComponent {

    private final JPanel panel;
    private final JBTextField urlText = new JBTextField();
    private final JBTextField apiKeyText = new JBTextField();
    private final JBTextField temperatureText = new JBTextField();
    private final JBTextField topPText = new JBTextField();
    private final JBTextField maxTokensText = new JBTextField();
    private final JBTextField timeoutText = new JBTextField();
    private final JBCheckBox streamModeCheckBox = new JBCheckBox("Enable stream mode", true);

    public SettingsComponent() {
        PluginStateService state = PluginStateService.getInstance();
        urlText.setText(state.getLlamaCppUrl());
        apiKeyText.setText(state.getApiKey());
        temperatureText.setText(String.valueOf(state.getTemperature()));
        topPText.setText(String.valueOf(state.getTopP()));
        maxTokensText.setText(String.valueOf(state.getMaxTokens()));
        timeoutText.setText(String.valueOf(state.getTimeout()));
        streamModeCheckBox.setSelected(state.isStreamMode());

        panel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Llama.cpp Server URL:"), urlText)
                .addLabeledComponent(new JBLabel("API Key:"), apiKeyText)
                .addLabeledComponent(new JBLabel("Temperature (0.0-1.0):"), temperatureText)
                .addLabeledComponent(new JBLabel("Top P (0.0-1.0):"), topPText)
                .addLabeledComponent(new JBLabel("Max Output Tokens:"), maxTokensText)
                .addLabeledComponent(new JBLabel("Timeout (seconds):"), timeoutText)
                .addComponent(streamModeCheckBox)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    public JPanel getPanel() {
        return panel;
    }

    public @NotNull String getUrlText() {
        return urlText.getText();
    }

    public void setUrlText(String text) {
        urlText.setText(text);
    }

    public @NotNull String getApiKeyText() {
        return apiKeyText.getText();
    }

    public void setApiKeyText(String text) {
        apiKeyText.setText(text);
    }

    public double getTemperature() {
        try {
            return Double.parseDouble(temperatureText.getText());
        } catch (NumberFormatException e) {
            return 0.7;
        }
    }

    public void setTemperature(double value) {
        temperatureText.setText(String.valueOf(value));
    }

    public double getTopP() {
        try {
            return Double.parseDouble(topPText.getText());
        } catch (NumberFormatException e) {
            return 0.9;
        }
    }

    public void setTopP(double value) {
        topPText.setText(String.valueOf(value));
    }

    public int getMaxTokens() {
        try {
            return Integer.parseInt(maxTokensText.getText());
        } catch (NumberFormatException e) {
            return 4000;
        }
    }

    public void setMaxTokens(int value) {
        maxTokensText.setText(String.valueOf(value));
    }

    public int getTimeout() {
        try {
            return Integer.parseInt(timeoutText.getText());
        } catch (NumberFormatException e) {
            return 120;
        }
    }

    public void setTimeout(int value) {
        timeoutText.setText(String.valueOf(value));
    }

    public boolean isStreamMode() {
        return streamModeCheckBox.isSelected();
    }

    public void setStreamMode(boolean selected) {
        streamModeCheckBox.setSelected(selected);
    }
}
