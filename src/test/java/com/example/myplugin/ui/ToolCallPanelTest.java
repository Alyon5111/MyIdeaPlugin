package com.example.myplugin.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallPanelTest {

    @Test
    void collapsesByDefault() {
        ToolCallPanel panel = new ToolCallPanel("read_file", "{\"path\":\"a.java\"}");
        boolean expanded = false;
        for (java.awt.Component c : panel.getComponents()) {
            if (c instanceof javax.swing.JScrollPane sp) {
                expanded = sp.isVisible();
            }
        }
        assertFalse(expanded, "tool call panel should be collapsed by default");
    }

    @Test
    void buildWithLongArgumentsTruncatesNothingExploding() {
        String longArgs = "{".repeat(200);
        ToolCallPanel panel = new ToolCallPanel("edit_file", longArgs);
        assertNotNull(panel);
    }

    @Test
    void appendResultKeepsPanelUsable() {
        ToolCallPanel panel = new ToolCallPanel("execute_command", "gradle test");
        panel.appendResult("BUILD SUCCESSFUL");
        assertNotNull(panel);
    }
}