package com.example.myplugin.ui;

import com.example.myplugin.settings.PluginStateService;
import com.example.myplugin.settings.SettingsComponent;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import java.awt.*;

public class SettingsPanel extends JPanel {

    private final SettingsComponent settingsComponent;

    public SettingsPanel() {
        super(new BorderLayout());
        settingsComponent = new SettingsComponent();

        add(new JBScrollPane(settingsComponent.getPanel()), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> save());
        buttonPanel.add(saveButton);

        JButton resetButton = new JButton("Reset");
        resetButton.addActionListener(e -> reset());
        buttonPanel.add(resetButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void save() {
        PluginStateService state = PluginStateService.getInstance();
        state.setLlamaCppUrl(settingsComponent.getUrlText());
        state.setApiKey(settingsComponent.getApiKeyText());
        state.setTemperature(settingsComponent.getTemperature());
        state.setTopP(settingsComponent.getTopP());
        state.setMaxTokens(settingsComponent.getMaxTokens());
        state.setTimeout(settingsComponent.getTimeout());
        state.setStreamMode(settingsComponent.isStreamMode());
        JOptionPane.showMessageDialog(this, "Settings saved!", "Settings", JOptionPane.INFORMATION_MESSAGE);
    }

    private void reset() {
        PluginStateService state = PluginStateService.getInstance();
        settingsComponent.setUrlText(state.getLlamaCppUrl());
        settingsComponent.setApiKeyText(state.getApiKey());
        settingsComponent.setTemperature(state.getTemperature());
        settingsComponent.setTopP(state.getTopP());
        settingsComponent.setMaxTokens(state.getMaxTokens());
        settingsComponent.setTimeout(state.getTimeout());
        settingsComponent.setStreamMode(state.isStreamMode());
    }
}
