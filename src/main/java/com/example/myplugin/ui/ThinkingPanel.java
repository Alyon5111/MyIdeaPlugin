package com.example.myplugin.ui;

import com.intellij.ui.JBColor;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ThinkingPanel extends JPanel {

    private final JLabel toggleLabel;
    private final JTextPane contentPane;
    private boolean collapsed = true;
    private String fullText = "";

    public ThinkingPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createLineBorder(JBColor.border(), 1));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UIManager.getColor("Panel.background"));
        header.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        toggleLabel = new JLabel("\u25B6 Thinking");
        toggleLabel.setFont(new Font("Dialog", Font.ITALIC, 12));
        toggleLabel.setForeground(JBColor.GRAY);
        header.add(toggleLabel, BorderLayout.WEST);

        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggle();
            }
        });

        add(header, BorderLayout.NORTH);

        contentPane = new JTextPane();
        contentPane.setEditable(false);
        contentPane.setFont(new Font("Monospaced", Font.PLAIN, 12));
        contentPane.setForeground(JBColor.GRAY);
        contentPane.setBackground(UIManager.getColor("Panel.background"));

        JScrollPane scrollPane = new JScrollPane(contentPane);
        scrollPane.setPreferredSize(new Dimension(0, 150));
        scrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
        scrollPane.setVisible(false);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void setThinkingText(String text) {
        fullText = text;
        collapsed = true;
        updateLabel();
        contentPane.setText("");
        try {
            StyledDocument doc = contentPane.getStyledDocument();
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setFontFamily(attrs, "Monospaced");
            StyleConstants.setFontSize(attrs, 12);
            StyleConstants.setForeground(attrs, JBColor.GRAY);
            doc.insertString(0, text, attrs);
        } catch (BadLocationException ignored) {
        }
        JScrollPane sp = getScrollPane();
        if (sp != null) {
            sp.setVisible(false);
        }
        setVisible(true);
    }

    public void startStreaming() {
        fullText = "";
        contentPane.setText("");
        collapsed = true;
        updateLabel();
        JScrollPane sp = getScrollPane();
        if (sp != null) {
            sp.setVisible(false);
        }
        setVisible(true);
    }

    public void appendThinking(String text) {
        fullText += text;
        SwingUtilities.invokeLater(() -> {
            try {
                StyledDocument doc = contentPane.getStyledDocument();
                SimpleAttributeSet attrs = new SimpleAttributeSet();
                StyleConstants.setFontFamily(attrs, "Monospaced");
                StyleConstants.setFontSize(attrs, 12);
                StyleConstants.setForeground(attrs, JBColor.GRAY);
                doc.insertString(doc.getLength(), text, attrs);
                contentPane.setCaretPosition(doc.getLength());
            } catch (BadLocationException ignored) {
            }
            updateLabel();
        });
    }

    public void finishStreaming() {
        updateLabel();
    }

    public String getThinkingText() {
        return fullText;
    }

    public void reset() {
        fullText = "";
        contentPane.setText("");
        setVisible(false);
    }

    private void toggle() {
        collapsed = !collapsed;
        JScrollPane sp = getScrollPane();
        if (sp != null) {
            sp.setVisible(!collapsed);
        }
        updateLabel();
        revalidate();
        repaint();
    }

    private void updateLabel() {
        if (fullText.isEmpty()) {
            toggleLabel.setText("\u25B6 Thinking");
        } else {
            String arrow = collapsed ? "\u25B6" : "\u25BC";
            toggleLabel.setText(arrow + " Thinking (" + fullText.length() + " chars)");
        }
    }

    private JScrollPane getScrollPane() {
        for (Component c : getComponents()) {
            if (c instanceof JScrollPane) {
                return (JScrollPane) c;
            }
        }
        return null;
    }
}
