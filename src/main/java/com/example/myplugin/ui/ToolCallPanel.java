package com.example.myplugin.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ToolCallPanel extends JPanel {

    private final JTextArea contentArea;
    private boolean expanded = false;
    private final JScrollPane scrollPane;

    public ToolCallPanel(String toolName, String arguments) {
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(new Color(50, 55, 65));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 85, 95)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        header.setOpaque(false);

        JLabel icon = new JLabel("\uD83D\uDD27"); // wrench emoji
        icon.setFont(new Font("Dialog", Font.PLAIN, 12));
        header.add(icon);

        JLabel nameLabel = new JLabel(toolName);
        nameLabel.setFont(new Font("Monospaced", Font.BOLD, 11));
        nameLabel.setForeground(new Color(130, 180, 255));
        header.add(nameLabel);

        String preview = arguments;
        if (preview.length() > 60) {
            preview = preview.substring(0, 60) + "...";
        }
        JLabel argPreview = new JLabel(preview);
        argPreview.setFont(new Font("Dialog", Font.PLAIN, 10));
        argPreview.setForeground(Color.GRAY);
        header.add(argPreview);

        JLabel toggleLabel = new JLabel("+");
        toggleLabel.setFont(new Font("Dialog", Font.PLAIN, 12));
        toggleLabel.setForeground(Color.GRAY);
        toggleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        header.add(toggleLabel);

        contentArea = new JTextArea(toolName + "\n\n" + arguments);
        contentArea.setEditable(false);
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);
        contentArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        contentArea.setBackground(new Color(40, 44, 52));
        contentArea.setForeground(new Color(170, 170, 170));
        contentArea.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        scrollPane = new JScrollPane(contentArea);
        scrollPane.setPreferredSize(new Dimension(0, 150));
        scrollPane.setVisible(false);

        toggleLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                expanded = !expanded;
                scrollPane.setVisible(expanded);
                toggleLabel.setText(expanded ? "-" : "+");
                revalidate();
                repaint();
                setMaximumSize(expanded
                        ? new Dimension(Integer.MAX_VALUE, 250)
                        : new Dimension(Integer.MAX_VALUE, 200));
            }
        });

        add(header, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void appendResult(String result) {
        contentArea.append("\n\n--- Result ---\n" + result);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, expanded ? 250 : 200));
    }
}
