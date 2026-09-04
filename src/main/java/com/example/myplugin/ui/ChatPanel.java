package com.example.myplugin.ui;

import com.example.myplugin.agent.AgentExecutor;
import com.example.myplugin.chatmodel.ChatModelFactory;
import com.example.myplugin.chatmodel.ChatModelFactoryProvider;
import com.example.myplugin.chatmodel.local.llamacpp.LlamaChatStreamClient;
import com.example.myplugin.chatmodel.local.llamacpp.LlamaModelService;
import com.example.myplugin.chatmodel.local.llamacpp.LlamaSSEClient;
import com.example.myplugin.model.Conversation;
import com.example.myplugin.model.CustomChatModel;
import com.example.myplugin.model.LanguageModel;
import com.example.myplugin.model.ModelProvider;
import com.example.myplugin.service.ConversationService;
import com.example.myplugin.agent.memory.AgentMemoryService;
import com.example.myplugin.agent.memory.semanticmemory.ConversationSemanticMemory;
import com.example.myplugin.settings.PluginStateService;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatPanel extends JPanel {

    private final JComboBox<LanguageModel> modelComboBox;
    private final JLabel statusLabel;
    private final JTextArea inputArea;
    private final JButton sendButton;
    private final JButton stopButton;
    private volatile boolean isStreaming = false;
    private volatile Thread currentStreamThread;
    private final ConversationService conversationService;

    private final JTabbedPane conversationTabs;
    private final Map<String, JPanel> messagesPanelMap = new HashMap<>();
    private final Map<String, JScrollPane> scrollPaneMap = new HashMap<>();

    // Streaming state
    private ThinkingPanel currentThinkingPanel;
    private JTextArea currentAnswerArea;

    // Agent state
    private final JCheckBox agentToggle;
    private AgentExecutor agentExecutor;
    private Thread agentThread;
    private boolean programmaticTabChange = false;
    private Project project;

    public ChatPanel(Project project, ConversationService conversationService) {
        super(new BorderLayout());
        this.conversationService = conversationService;
        this.project = project;
        if (project != null) {
            agentExecutor = new AgentExecutor(project);
        }

        // Top bar: model selector + refresh
        JPanel topBar = new JPanel(new BorderLayout());
        modelComboBox = new JComboBox<>();
        topBar.add(new JLabel(" Model: "), BorderLayout.WEST);
        topBar.add(modelComboBox, BorderLayout.CENTER);

        JButton refreshButton = new JButton("\u21BB");
        refreshButton.setToolTipText("Refresh models");
        refreshButton.addActionListener(e -> {
            LlamaModelService.getInstance().resetModels();
            refreshModels();
        });

        JPanel eastPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        agentToggle = new JCheckBox("Agent");
        agentToggle.setToolTipText("Toggle Agent mode (tool calling)");
        agentToggle.setFont(new Font("Dialog", Font.BOLD, 11));
        eastPanel.add(agentToggle);
        eastPanel.add(refreshButton);
        topBar.add(eastPanel, BorderLayout.EAST);

        statusLabel = new JLabel(" ");
        statusLabel.setFont(new Font("Dialog", Font.ITALIC, 11));
        statusLabel.setForeground(Color.GRAY);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        topBar.add(statusLabel, BorderLayout.SOUTH);

        add(topBar, BorderLayout.NORTH);

        // Conversation tabs
        conversationTabs = new JTabbedPane(JTabbedPane.TOP);
        conversationTabs.addTab("+", new JPanel());
        add(conversationTabs, BorderLayout.CENTER);

        // Tab change listener
        conversationTabs.addChangeListener(e -> {
            if (programmaticTabChange) return;
            int idx = conversationTabs.getSelectedIndex();
            if (idx < 0) return;

            // "+" tab clicked - create new conversation
            if (isAddTab(idx)) {
                int prevIdx = Math.max(0, idx - 1);
                conversationTabs.setSelectedIndex(prevIdx);
                createNewTab();
                return;
            }

            String convId = getTabConvId(idx);
            if (convId != null) {
                Conversation conv = conversationService.findConversationById(convId);
                if (conv != null && conv != conversationService.getCurrentConversation()) {
                    conversationService.switchTo(conv);
                }
            }
        });

        refreshModels();

        // Load model on selection change
        modelComboBox.addActionListener(e -> {
            LanguageModel selected = (LanguageModel) modelComboBox.getSelectedItem();
            if (selected == null) return;

            String modelName = selected.getModelName();
            String currentStatus = selected.getStatus();
            System.out.println("[MyPlugin] Model selected: " + modelName + ", cached status: " + currentStatus);

            if ("loaded".equals(currentStatus)) {
                System.out.println("[MyPlugin] Model already marked loaded, skipping load");
                setStatus("Ready - " + modelName);
                return;
            }

            new Thread(() -> {
                try {
                    LlamaSSEClient sse = LlamaSSEClient.getInstance();
                    SwingUtilities.invokeLater(() -> modelComboBox.setEnabled(false));

                    if (!sse.isConnected()) {
                        sse.connect();
                        if (!sse.waitForConnection(10)) {
                            String err = sse.getLastError();
                            sse.disconnect();
                            SwingUtilities.invokeLater(() -> {
                                modelComboBox.setEnabled(true);
                                setStatus("SSE connect failed: " + (err != null ? err : "unknown"));
                            });
                            return;
                        }
                    }

                    sse.setProgressCallback(progress -> SwingUtilities.invokeLater(() -> {
                        if ("loaded".equals(progress.status) || "sleeping".equals(progress.status)) {
                            modelComboBox.setEnabled(true);
                            setStatus("Ready");
                        } else if ("loading".equals(progress.status)) {
                            if (!progress.currentStage.isEmpty()) {
                                int stageNum = progress.getStageIndex() + 1;
                                int stageTotal = progress.getStageCount();
                                setStatus("Loading " + progress.model + " "
                                        + progress.currentStage + "[" + stageNum + "/" + stageTotal + "] "
                                        + progress.getPercent() + "%");
                            } else {
                                setStatus("Loading " + progress.model + " " + progress.getPercent() + "%");
                            }
                        } else {
                            setStatus(progress.status);
                        }
                    }));

                    System.out.println("[MyPlugin] Loading model: " + modelName);
                    sse.prepareWait(modelName);
                    LlamaModelService.getInstance().loadModel(modelName);
                    boolean loaded = sse.waitForModel(120);
                    System.out.println("[MyPlugin] Model load result: " + loaded);
                    SwingUtilities.invokeLater(() -> {
                        modelComboBox.setEnabled(true);
                        setStatus(loaded ? "Ready" : "Load timeout");
                    });
                } catch (Exception ex) {
                    System.err.println("[MyPlugin] Load failed: " + ex.getMessage());
                    ex.printStackTrace();
                    SwingUtilities.invokeLater(() -> {
                        modelComboBox.setEnabled(true);
                        setStatus("Load failed: " + ex.getMessage());
                    });
                }
            }).start();
        });

        // Bottom: input area + buttons
        Color inputBg = new Color(43, 43, 49);
        Color inputFg = new Color(187, 187, 187);

        JPanel inputWrapper = new JPanel(new BorderLayout());
        inputWrapper.setBackground(inputBg);
        inputWrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(85, 85, 85)),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));

        inputArea = new JTextArea(8, 40);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setOpaque(false);
        inputArea.setBackground(inputBg);
        inputArea.setForeground(inputFg);
        inputArea.setCaretColor(Color.WHITE);
        inputArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        inputWrapper.add(inputArea, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(inputBg);
        bottomPanel.add(inputWrapper, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        stopButton = new JButton("Stop");
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> stopStreaming());
        buttonPanel.add(stopButton);

        sendButton = new JButton("Send");
        sendButton.addActionListener(e -> sendMessage());
        buttonPanel.add(sendButton);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);

        // Enter sends, Shift+Enter newline
        inputArea.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "send");
        inputArea.getInputMap().put(KeyStroke.getKeyStroke("shift ENTER"), "newline");
        inputArea.getActionMap().put("send", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                sendMessage();
            }
        });
        inputArea.getActionMap().put("newline", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                inputArea.insert("\n", inputArea.getCaretPosition());
            }
        });
    }

    public void loadConversation(Conversation conv) {
        if (conv == null) return;

        String convId = conv.getId();
        if (!messagesPanelMap.containsKey(convId)) {
            createTabForConversation(conv);
        }

        int tabIdx = findTabIndex(convId);
        if (tabIdx >= 0) {
            programmaticTabChange = true;
            try {
                conversationTabs.setSelectedIndex(tabIdx);
            } finally {
                programmaticTabChange = false;
            }
        }
    }

    private void createNewTab() {
        Conversation conv = conversationService.createNewConversation(null);
        // createTabForConversation will be called via loadConversation -> onConversationChanged
    }

    private void createTabForConversation(Conversation conv) {
        JPanel messagesPanel = new JPanel();
        messagesPanel.setLayout(new BoxLayout(messagesPanel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JBScrollPane(messagesPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        messagesPanelMap.put(conv.getId(), messagesPanel);
        scrollPaneMap.put(conv.getId(), scrollPane);

        int addTabIdx = conversationTabs.getTabCount() - 1;
        programmaticTabChange = true;
        try {
            conversationTabs.insertTab(conv.getTitle(), null, scrollPane, null, addTabIdx);
            conversationTabs.setTabComponentAt(addTabIdx, buildTabComponent(conv));
        } finally {
            programmaticTabChange = false;
        }

        rebuildMessages(messagesPanel, conv);
    }

    private JPanel buildTabComponent(Conversation conv) {
        JPanel tabComponent = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        tabComponent.setOpaque(false);
        tabComponent.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int idx = findTabIndex(conv.getId());
                if (idx >= 0) {
                    conversationTabs.setSelectedIndex(idx);
                }
            }
        });
        JLabel titleLabel = new JLabel(conv.getTitle());
        titleLabel.setFont(new Font("Dialog", Font.BOLD, 12));
        titleLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    startRename(tabComponent, conv);
                } else {
                    int idx = findTabIndex(conv.getId());
                    if (idx >= 0) {
                        conversationTabs.setSelectedIndex(idx);
                    }
                }
            }
        });
        tabComponent.add(titleLabel);
        JLabel closeLabel = new JLabel("\u2715");
        closeLabel.setFont(new Font("Dialog", Font.PLAIN, 10));
        closeLabel.setForeground(Color.GRAY);
        closeLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                closeTab(conv);
            }

            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                closeLabel.setForeground(Color.RED);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                closeLabel.setForeground(Color.GRAY);
            }
        });
        tabComponent.add(closeLabel);
        return tabComponent;
    }

    private void startRename(JPanel tabComponent, Conversation conv) {
        JTextField field = new JTextField(conv.getTitle(), 12);
        field.setFont(new Font("Dialog", Font.BOLD, 12));
        field.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        tabComponent.removeAll();
        tabComponent.add(field);
        tabComponent.revalidate();
        tabComponent.repaint();
        field.requestFocusInWindow();
        field.selectAll();

        Runnable finishRename = () -> {
            String newName = field.getText().trim();
            if (!newName.isEmpty()) {
                conv.setTitle(newName);
                conversationService.save();
            }
            int idx = findTabIndex(conv.getId());
            if (idx >= 0) {
                conversationTabs.setTitleAt(idx, conv.getTitle());
                conversationTabs.setTabComponentAt(idx, buildTabComponent(conv));
            }
        };

        field.addActionListener(e -> finishRename.run());
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                finishRename.run();
            }
        });
    }

    private boolean isAddTab(int idx) {
        return idx == conversationTabs.getTabCount() - 1
                && "+".equals(conversationTabs.getTitleAt(idx));
    }

    private void closeTab(Conversation conv) {
        int idx = findTabIndex(conv.getId());
        if (idx < 0) return;

        if (conversationService.size() <= 1) {
            return;
        }

        Conversation nextConv = null;
        for (Conversation c : conversationService.getConversations()) {
            if (c != conv) {
                nextConv = c;
                break;
            }
        }

        conversationTabs.removeTabAt(idx);
        messagesPanelMap.remove(conv.getId());
        scrollPaneMap.remove(conv.getId());
        conversationService.getConversations().remove(conv);

        if (nextConv != null) {
            conversationService.switchTo(nextConv);
        }
    }

    private int findTabIndex(String convId) {
        for (int i = 0; i < conversationTabs.getTabCount(); i++) {
            if (convId.equals(getTabConvId(i))) {
                return i;
            }
        }
        return -1;
    }

    private String getTabConvId(int tabIdx) {
        if (tabIdx < 0 || tabIdx >= conversationTabs.getTabCount()) return null;
        Component comp = conversationTabs.getComponentAt(tabIdx);
        if (comp instanceof JScrollPane sp) {
            for (var entry : scrollPaneMap.entrySet()) {
                if (entry.getValue() == sp) return entry.getKey();
            }
        }
        return null;
    }

    public void refreshTabTitle(Conversation conv) {
        int idx = findTabIndex(conv.getId());
        if (idx >= 0) {
            conversationTabs.setTitleAt(idx, conv.getTitle());
            conversationTabs.setTabComponentAt(idx, buildTabComponent(conv));
        }
    }

    private JPanel getCurrentMessagesPanel() {
        int idx = conversationTabs.getSelectedIndex();
        if (idx < 0) return null;
        String convId = getTabConvId(idx);
        return convId != null ? messagesPanelMap.get(convId) : null;
    }

    private JScrollPane getCurrentScrollPane() {
        int idx = conversationTabs.getSelectedIndex();
        if (idx < 0) return null;
        String convId = getTabConvId(idx);
        return convId != null ? scrollPaneMap.get(convId) : null;
    }

    private void rebuildMessages(JPanel messagesPanel, Conversation conv) {
        messagesPanel.removeAll();
        int total = conv.getMessages().size();
        int idx = 0;
        for (Conversation.ChatMessage msg : conv.getMessages()) {
            idx++;
            if (msg.getRole() == Conversation.Role.USER) {
                addUserMessage(messagesPanel, msg.getContent());
            } else {
                boolean hasThinking = msg.getThinking() != null && !msg.getThinking().isEmpty();
                if (hasThinking) {
                    ThinkingPanel tp = createThinkingPanel();
                    tp.setThinkingText(msg.getThinking());
                    messagesPanel.add(tp);
                }
                addAssistantMessage(messagesPanel, msg.getContent());
            }
            if (idx < total) {
                messagesPanel.add(createSeparator());
            }
        }
        messagesPanel.revalidate();
        messagesPanel.repaint();
    }

    private Component createSeparator() {
        JPanel wrapper = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(new Color(180, 180, 190));
                g2.setStroke(new BasicStroke(1.5f));
                int y = getHeight() / 2;
                g2.drawLine(10, y, getWidth() - 10, y);
            }
        };
        wrapper.setOpaque(false);
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 12));
        wrapper.setPreferredSize(new Dimension(0, 12));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        return wrapper;
    }

    private ThinkingPanel createThinkingPanel() {
        ThinkingPanel tp = new ThinkingPanel();
        tp.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
        tp.setAlignmentX(Component.LEFT_ALIGNMENT);
        return tp;
    }

    private void addUserMessage(JPanel messagesPanel, String text) {
        JTextArea area = new JTextArea("You: " + text);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font("Dialog", Font.PLAIN, 13));
        area.setBackground(UIManager.getColor("Panel.background"));
        area.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        area.setMaximumSize(new Dimension(Integer.MAX_VALUE, area.getPreferredSize().height + 10));
        messagesPanel.add(area);
        messagesPanel.add(Box.createVerticalStrut(4));
    }

    private void addAssistantMessage(JPanel messagesPanel, String text) {
        JTextArea area = new JTextArea("Assistant:\n" + text);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font("Monospaced", Font.PLAIN, 13));
        area.setBackground(UIManager.getColor("Panel.background"));
        area.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        messagesPanel.add(area);
        messagesPanel.add(Box.createVerticalStrut(8));
    }

    public void refreshModels() {
        ActionListener[] listeners = modelComboBox.getActionListeners();
        for (ActionListener l : listeners) {
            modelComboBox.removeActionListener(l);
        }

        modelComboBox.removeAllItems();
        try {
            LlamaModelService.getInstance().resetModels();
            List<LanguageModel> models = LlamaModelService.getInstance().getModels();
            LanguageModel toSelect = null;
            for (LanguageModel model : models) {
                modelComboBox.addItem(model);
                if ("loaded".equals(model.getStatus())) {
                    toSelect = model;
                } else if (toSelect == null && "loading".equals(model.getStatus())) {
                    toSelect = model;
                }
            }
            if (toSelect != null) {
                modelComboBox.setSelectedItem(toSelect);
            }
            setStatus("Found " + models.size() + " model(s)");
        } catch (Exception e) {
            modelComboBox.addItem(LanguageModel.builder()
                    .provider(ModelProvider.LLaMA)
                    .modelName("default")
                    .displayName("default (Llama.cpp not running)")
                    .inputCost(0)
                    .outputCost(0)
                    .inputMaxTokens(8192)
                    .apiKeyUsed(false)
                    .build());
            setStatus("Refresh failed: " + e.getMessage());
        }

        for (ActionListener l : listeners) {
            modelComboBox.addActionListener(l);
        }
    }

    private void sendMessage() {
        String prompt = inputArea.getText().trim();
        if (prompt.isEmpty() || isStreaming) {
            return;
        }

        LanguageModel selectedModel = (LanguageModel) modelComboBox.getSelectedItem();
        if (selectedModel == null) {
            return;
        }

        Conversation conv = conversationService.getCurrentConversation();
        if (conv == null) {
            return;
        }

        if (conv.getMessages().isEmpty()) {
            conv.setTitle(prompt.length() > 30 ? prompt.substring(0, 30) + "..." : prompt);
            refreshTabTitle(conv);
        }

        conv.addMessage(Conversation.Role.USER, prompt);
        conversationService.save();

        JPanel messagesPanel = getCurrentMessagesPanel();
        JScrollPane scrollPane = getCurrentScrollPane();
        if (messagesPanel == null || scrollPane == null) return;

        inputArea.setText("");
        rebuildMessages(messagesPanel, conv);

        if (agentToggle.isSelected() && agentExecutor != null) {
            agentResponse(messagesPanel, scrollPane, conv, selectedModel.getModelName());
        } else {
            chatResponse(messagesPanel, scrollPane, selectedModel, conv);
        }
    }

    private void chatResponse(JPanel messagesPanel, JScrollPane scrollPane, LanguageModel selectedModel, Conversation conv) {
        // Prepare streaming UI components
        currentThinkingPanel = createThinkingPanel();
        currentThinkingPanel.startStreaming();
        messagesPanel.add(currentThinkingPanel);

        currentAnswerArea = new JTextArea("Assistant:\n");
        currentAnswerArea.setEditable(false);
        currentAnswerArea.setLineWrap(true);
        currentAnswerArea.setWrapStyleWord(true);
        currentAnswerArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        currentAnswerArea.setBackground(UIManager.getColor("Panel.background"));
        currentAnswerArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        currentAnswerArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        messagesPanel.add(currentAnswerArea);
        messagesPanel.add(Box.createVerticalStrut(8));

        messagesPanel.revalidate();
        scrollToBottom(scrollPane);
        setStreaming(true);

        PluginStateService state = PluginStateService.getInstance();

        if (state.isStreamMode()) {
            streamResponse(messagesPanel, scrollPane, selectedModel, state, conv);
        } else {
            nonStreamResponse(messagesPanel, scrollPane, selectedModel, state, conv);
        }
    }

    private void agentResponse(JPanel messagesPanel, JScrollPane scrollPane, Conversation conv, String modelName) {
        setStreaming(true);
        setStatus("Agent working...");

        ThinkingPanel thinkingPanel = createThinkingPanel();
        thinkingPanel.startStreaming();
        thinkingPanel.expand();
        messagesPanel.add(thinkingPanel);
        messagesPanel.revalidate();
        scrollToBottom(scrollPane);

        Conversation.ChatMessage agentMsg = new Conversation.ChatMessage(Conversation.Role.ASSISTANT, "");

        agentThread = new Thread(() -> {
            StringBuilder thinkingBuf = new StringBuilder();
            try {
                agentExecutor.execute(conv.getMessages(), modelName, new AgentExecutor.AgentEvent() {
                    @Override
                    public void onThinking(String text) {
                        String entry = "[Thinking] " + text + "\n";
                        thinkingBuf.append(entry);
                        thinkingPanel.appendThinking(entry);
                        SwingUtilities.invokeLater(() -> scrollToBottom(scrollPane));
                    }

                    @Override
                    public void onToolCall(String toolName, String arguments) {
                        String entry = "[Tool Call] " + toolName + ": " + arguments + "\n";
                        thinkingBuf.append(entry);
                        thinkingPanel.appendThinking(entry);
                        SwingUtilities.invokeLater(() -> scrollToBottom(scrollPane));
                    }

                    @Override
                    public void onToolResult(String toolName, String result) {
                        String entry = "[Tool Result] " + toolName + ": " + result + "\n\n";
                        thinkingBuf.append(entry);
                        thinkingPanel.appendThinking(entry);
                        SwingUtilities.invokeLater(() -> scrollToBottom(scrollPane));
                    }

                    @Override
                    public void onAnswer(String text) {
                        String entry = "[Answer] " + text + "\n";
                        thinkingBuf.append(entry);
                        thinkingPanel.appendThinking(entry);
                        SwingUtilities.invokeLater(() -> scrollToBottom(scrollPane));
                    }

                    @Override
                    public void onError(String error) {
                        String entry = "[Error] " + error + "\n";
                        thinkingBuf.append(entry);
                        thinkingPanel.appendThinking(entry);
                        SwingUtilities.invokeLater(() -> scrollToBottom(scrollPane));
                    }
                });

                thinkingPanel.finishStreaming();
                agentMsg.setThinking(thinkingBuf.toString());

                String finalAnswer = agentExecutor.getLastAnswer();
                agentMsg.setContent(finalAnswer != null ? finalAnswer : "");
                conv.getMessages().add(agentMsg);
                conversationService.save();

                if (project != null && PluginStateService.getInstance().isMemoryEnabled()) {
                    try {
                        AgentMemoryService memoryService = new AgentMemoryService(project);
                        ConversationSemanticMemory semantic = new ConversationSemanticMemory(memoryService);
                        int stored = semantic.extractAndStore(conv);
                        if (stored > 0) {
                            thinkingBuf.append("[Memory] Extracted ").append(stored).append(" memory entr").append(stored == 1 ? "y" : "ies").append("\n");
                            thinkingPanel.appendThinking("[Memory] Extracted " + stored + " memory entr" + (stored == 1 ? "y" : "ies") + "\n");
                        }
                    } catch (Exception e) {
                        thinkingBuf.append("[Memory] Extraction failed: ").append(e.getMessage()).append("\n");
                    }
                }

                SwingUtilities.invokeLater(() -> {
                    rebuildMessages(messagesPanel, conv);
                    scrollToBottom(scrollPane);
                    refreshTabTitle(conv);
                    setStreaming(false);
                    setStatus("Agent done");
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    addAssistantMessage(messagesPanel, "Agent error: " + e.getMessage());
                    scrollToBottom(scrollPane);
                    setStreaming(false);
                    setStatus("Agent error");
                });
            }
        }, "agent-executor");
        agentThread.start();
    }

    private void streamResponse(JPanel messagesPanel, JScrollPane scrollPane, LanguageModel selectedModel, PluginStateService state, Conversation conv) {
        LlamaChatStreamClient.streamChat(
                selectedModel.getModelName(),
                conv.getMessages(),
                (type, text) -> {
                    SwingUtilities.invokeLater(() -> {
                        if ("reasoning".equals(type)) {
                            currentThinkingPanel.appendThinking(text);
                        } else {
                            currentAnswerArea.append(text);
                            scrollToBottom(scrollPane);
                        }
                    });
                },
                (reasoning, content) -> {
                    boolean hasThinking = !reasoning.isEmpty();
                    Conversation.ChatMessage assistantMsg = new Conversation.ChatMessage(Conversation.Role.ASSISTANT, content);
                    assistantMsg.setThinking(reasoning);
                    conv.getMessages().add(assistantMsg);
                    conv.setUpdatedAt(java.time.LocalDateTime.now());
                    conversationService.save();

                    SwingUtilities.invokeLater(() -> {
                        if (hasThinking) {
                            currentThinkingPanel.finishStreaming();
                        } else {
                            messagesPanel.remove(currentThinkingPanel);
                        }
                        currentThinkingPanel = null;
                        currentAnswerArea = null;
                        messagesPanel.revalidate();
                        messagesPanel.repaint();
                        scrollToBottom(scrollPane);
                        refreshTabTitle(conv);
                    });
                    setStreaming(false);
                },
                (isTimeout, errorMsg) -> {
                    SwingUtilities.invokeLater(() -> {
                        currentAnswerArea.append("\n\nError: " + errorMsg);
                        messagesPanel.remove(currentThinkingPanel);
                        currentThinkingPanel = null;
                        currentAnswerArea = null;
                        messagesPanel.revalidate();
                        scrollToBottom(scrollPane);
                    });
                    setStreaming(false);
                }
        );
    }

    private void nonStreamResponse(JPanel messagesPanel, JScrollPane scrollPane, LanguageModel selectedModel, PluginStateService state, Conversation conv) {
        currentStreamThread = new Thread(() -> {
            try {
                ChatModelFactory factory = ChatModelFactoryProvider
                        .getFactoryByProvider(ModelProvider.LLaMA)
                        .orElseThrow(() -> new RuntimeException("No factory for LLaMA"));

                CustomChatModel custom = new CustomChatModel();
                custom.setBaseUrl(state.getLlamaCppUrl());
                custom.setModelName(selectedModel.getModelName());
                custom.setTemperature(state.getTemperature());
                custom.setTopP(state.getTopP());
                custom.setMaxTokens(state.getMaxTokens());
                custom.setTimeout(state.getTimeout());

                var chatModel = factory.createChatModel(custom);

                java.util.List<ChatMessage> history = new java.util.ArrayList<>();
                for (Conversation.ChatMessage msg : conv.getMessages()) {
                    if (msg.getRole() == Conversation.Role.USER) {
                        history.add(UserMessage.from(msg.getContent()));
                    } else {
                        history.add(AiMessage.from(msg.getContent()));
                    }
                }

                var response = chatModel.chat(history);
                String responseText = response.aiMessage().text();

                conv.addMessage(Conversation.Role.ASSISTANT, responseText);
                conversationService.save();
                SwingUtilities.invokeLater(() -> {
                    messagesPanel.remove(currentThinkingPanel);
                    currentThinkingPanel = null;
                    currentAnswerArea = null;
                    rebuildMessages(messagesPanel, conv);
                    scrollToBottom(scrollPane);
                    refreshTabTitle(conv);
                    setStreaming(false);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    currentAnswerArea.append("\n\nError: " + e.getMessage());
                    messagesPanel.remove(currentThinkingPanel);
                    currentThinkingPanel = null;
                    currentAnswerArea = null;
                    messagesPanel.revalidate();
                    scrollToBottom(scrollPane);
                    setStreaming(false);
                });
            }
        });
        currentStreamThread.start();
    }

    private void stopStreaming() {
        if (agentToggle.isSelected() && agentExecutor != null) {
            agentExecutor.cancel();
        }
        if (currentStreamThread != null) {
            currentStreamThread.interrupt();
            setStreaming(false);
        }
    }

    private void setStreaming(boolean streaming) {
        isStreaming = streaming;
        SwingUtilities.invokeLater(() -> {
            sendButton.setEnabled(!streaming);
            stopButton.setEnabled(streaming);
            inputArea.setEnabled(!streaming);
        });
    }

    private void scrollToBottom(JScrollPane scrollPane) {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    private void setStatus(String text) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(text));
    }
}
