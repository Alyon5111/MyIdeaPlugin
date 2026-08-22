package com.example.myplugin.ui;

import com.example.myplugin.model.Conversation;
import com.example.myplugin.service.ConversationService;
import com.intellij.ui.JBColor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;

public class ConversationListPanel extends JPanel {

    private final JPanel listPanel;
    private final ConversationService conversationService;
    private final Map<String, JPanel> buttonMap = new HashMap<>();

    public ConversationListPanel() {
        super(new BorderLayout());
        conversationService = ConversationService.getInstance();

        // Scrollable list
        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);


    }

    public void refreshList() {
        listPanel.removeAll();
        buttonMap.clear();

        for (Conversation conv : conversationService.getConversations()) {
            listPanel.add(createItem(conv));
            listPanel.add(Box.createVerticalStrut(2));
        }
        listPanel.revalidate();
        listPanel.repaint();
        highlightCurrent(conversationService.getCurrentConversation());
    }

    private JPanel createItem(Conversation conv) {
        JPanel item = new JPanel(new BorderLayout());
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        item.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 2));

        JLabel titleLabel = new JLabel(conv.getTitle());
        titleLabel.setFont(new Font("Dialog", Font.PLAIN, 12));
        item.add(titleLabel, BorderLayout.CENTER);

        JButton deleteBtn = new JButton("\u2715");
        deleteBtn.setFont(new Font("Dialog", Font.PLAIN, 10));
        deleteBtn.setMargin(new Insets(0, 2, 0, 2));
        deleteBtn.setBorderPainted(false);
        deleteBtn.setContentAreaFilled(false);
        deleteBtn.setForeground(JBColor.GRAY);
        deleteBtn.addActionListener(e -> {
            conversationService.deleteConversation(conv);
            refreshList();
        });
        item.add(deleteBtn, BorderLayout.EAST);

        item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        item.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                conversationService.switchTo(conv);
                refreshList();
            }
        });

        buttonMap.put(conv.getId(), item);
        return item;
    }

    private void highlightCurrent(Conversation current) {
        if (current == null) {
            return;
        }
        for (var entry : buttonMap.entrySet()) {
            JPanel item = entry.getValue();
            if (entry.getKey().equals(current.getId())) {
                item.setBackground(JBColor.border());
            } else {
                item.setBackground(null);
            }
            item.repaint();
        }
    }
}
