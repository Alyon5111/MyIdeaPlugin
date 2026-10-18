package com.example.myplugin.ui;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentExecutor;
import com.example.myplugin.agent.AgentTool;
import com.example.myplugin.agent.pipeline.PipelineService;
import com.example.myplugin.agent.pipeline.PipelineStage;
import com.example.myplugin.agent.pipeline.SDDPipelineTool;
import com.example.myplugin.agent.pipeline.SpecPipelineInstance;
import com.example.myplugin.agent.plan.ExportPlanTool;
import com.example.myplugin.openspec.tool.CreateChangeTool;
import com.example.myplugin.service.ConversationService;
import com.example.myplugin.service.TraceRecorder;
import com.google.gson.JsonObject;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;
import java.util.List;
import java.util.function.Function;

public class PipelinePanel extends JPanel {

    /** Test seam: executes a named tool, allowing unit tests to substitute a fake. */
    interface ToolRunner {
        String run(String toolName, JsonObject args);
    }

    private final Project project;
    private final ConversationService conversationService;
    private final PipelineService pipelineService;
    private final ToolRunner toolRunner;
    private AgentExecutor agentExecutor;

    // Package-private so unit tests in com.example.myplugin.ui can inspect state.
    final DefaultListModel<String> instanceModel = new DefaultListModel<>();
    final DefaultMutableTreeNode treeRoot = new DefaultMutableTreeNode("pipelines");
    final JTree instanceTree = new JTree(treeRoot);
    final JLabel summaryLabel = new JLabel("No active pipeline instances.");
    final JTextArea output = new JTextArea(12, 40);

    final JPopupMenu actionMenu = new JPopupMenu();
    final JMenuItem advancePlanItem = new JMenuItem("Advance Plan");
    final JMenuItem startTddItem = new JMenuItem("Start TDD");
    final JMenuItem executeStepItem = new JMenuItem("Execute Step");
    final JMenuItem completeItem = new JMenuItem("Complete");
    final JMenuItem exportPlanItem = new JMenuItem("Export Plan");
    final JMenuItem statusItem = new JMenuItem("Status");
    final JMenuItem listItem = new JMenuItem("List");
    final JMenuItem createChangeItem = new JMenuItem("Create Change");
    final JMenuItem linkChangeItem = new JMenuItem("Link Change");

    private SpecPipelineInstance[] instances = new SpecPipelineInstance[0];

    Function<String, String> changeNamePrompt = this::promptChangeName;

    /** Deterministic TDD scaffolding keyed by task text, so Execute Step can drive arbitrary new
     *  features (not just the Stop button). When a task matches, the model writes the exact test
     *  verbatim, performs the exact GREEN patch(es), and runs the enforcer cycle. A blueprint may
     *  carry several ordered edits; they are applied in order during the GREEN phase. */
    record TddBlueprint(String matchKeyword, String testFilePath, String testSource,
                        List<Edit> edits,
                        String sourceContextFile, String[] sourceContextKeywords) {
        /** One exact (oldString -> newString) replacement in a single file. */
        record Edit(String filePath, String oldString, String newString) {}

        boolean matches(String task) {
            return task != null && task.toLowerCase().contains(matchKeyword);
        }
    }

    private static final String BLUEPRINT_STOP_TEST = """
            package com.example.myplugin.ui;

            import com.example.myplugin.service.ConversationService;
            import org.junit.jupiter.api.Test;

            import static org.junit.jupiter.api.Assertions.*;

            class ChatPanelTest {

                private void flushEdt() {
                    try {
                        javax.swing.SwingUtilities.invokeAndWait(() -> { });
                    } catch (Exception ignored) {
                    }
                }

                @Test
                void stopDuringStreamingResetsUiState() {
                    ChatPanel panel = new ChatPanel(null, ConversationService.inMemory());
                    panel.simulateStreamingForTest();
                    flushEdt();
                    assertTrue(panel.isStreamingActive());
                    assertTrue(panel.isStopEnabled());
                    assertFalse(panel.isSendEnabled());

                    panel.stopStreaming();
                    flushEdt();

                    assertFalse(panel.isStreamingActive());
                    assertTrue(panel.isSendEnabled());
                    assertFalse(panel.isStopEnabled());
                }
            }
            """;

    private static final String BLUEPRINT_BUBBLE_TEST = """
            package com.example.myplugin.ui;

            import com.example.myplugin.service.ConversationService;
            import org.junit.jupiter.api.Test;

            import static org.junit.jupiter.api.Assertions.*;

            class ChatPanelTest {

                private void flushEdt() {
                    try {
                        javax.swing.SwingUtilities.invokeAndWait(() -> { });
                    } catch (Exception ignored) {
                    }
                }

                @Test
                void stopDuringStreamingResetsUiState() {
                    ChatPanel panel = new ChatPanel(null, ConversationService.inMemory());
                    panel.simulateStreamingForTest();
                    flushEdt();
                    assertTrue(panel.isStreamingActive());
                    assertTrue(panel.isStopEnabled());
                    assertFalse(panel.isSendEnabled());

                    panel.stopStreaming();
                    flushEdt();

                    assertFalse(panel.isStreamingActive());
                    assertTrue(panel.isSendEnabled());
                    assertFalse(panel.isStopEnabled());
                }

                @Test
                void renderBubbleHtmlPrefixesByRole() {
                    assertEquals("<html><body>You: Hello</body></html>",
                            ChatPanel.renderBubbleHtml("USER", "Hello"));
                    assertEquals("<html><body>You: Hello</body></html>",
                            ChatPanel.renderBubbleHtml("user", "Hello"));
                    assertEquals("<html><body>Assistant: Hello</body></html>",
                            ChatPanel.renderBubbleHtml("assistant", "Hello"));
                }

                @Test
                void renderBubbleHtmlTreatsUnknownRoleAsAssistant() {
                    assertEquals("<html><body>Assistant: Hi</body></html>",
                            ChatPanel.renderBubbleHtml("moderator", "Hi"));
                }

                @Test
                void renderBubbleHtmlEmptyAndNullContent() {
                    assertEquals("<html><body>You: </body></html>",
                            ChatPanel.renderBubbleHtml("user", null));
                    assertEquals("<html><body>You: </body></html>",
                            ChatPanel.renderBubbleHtml("USER", ""));
                    assertEquals("<html><body>Assistant: </body></html>",
                            ChatPanel.renderBubbleHtml("assistant", ""));
                }

                @Test
                void renderBubbleHtmlRendersBold() {
                    assertTrue(ChatPanel.renderBubbleHtml("assistant", "**bold**")
                            .contains("<strong>bold</strong>"));
                }

                @Test
                void renderBubbleHtmlWrapsParagraphs() {
                    String html = ChatPanel.renderBubbleHtml("user", "First\\n\\nSecond");
                    assertTrue(html.contains("<p>First</p>"));
                    assertTrue(html.contains("<p>Second</p>"));
                }
            }
            """;

    private static final String BLUEPRINT_STREAMING_TEST = """
            package com.example.myplugin.ui;

            import com.example.myplugin.service.ConversationService;
            import org.junit.jupiter.api.Test;

            import static org.junit.jupiter.api.Assertions.*;

            class ChatPanelTest {

                private void flushEdt() {
                    try {
                        javax.swing.SwingUtilities.invokeAndWait(() -> { });
                    } catch (Exception ignored) {
                    }
                }

                @Test
                void stopDuringStreamingResetsUiState() {
                    ChatPanel panel = new ChatPanel(null, ConversationService.inMemory());
                    panel.simulateStreamingForTest();
                    flushEdt();
                    assertTrue(panel.isStreamingActive());
                    assertTrue(panel.isStopEnabled());
                    assertFalse(panel.isSendEnabled());

                    panel.stopStreaming();
                    flushEdt();

                    assertFalse(panel.isStreamingActive());
                    assertTrue(panel.isSendEnabled());
                    assertFalse(panel.isStopEnabled());
                }

                @Test
                void renderBubbleHtmlPrefixesByRole() {
                    assertEquals("<html><body>You: Hello</body></html>",
                            ChatPanel.renderBubbleHtml("USER", "Hello"));
                    assertEquals("<html><body>You: Hello</body></html>",
                            ChatPanel.renderBubbleHtml("user", "Hello"));
                    assertEquals("<html><body>Assistant: Hello</body></html>",
                            ChatPanel.renderBubbleHtml("assistant", "Hello"));
                }

                @Test
                void renderBubbleHtmlTreatsUnknownRoleAsAssistant() {
                    assertEquals("<html><body>Assistant: Hi</body></html>",
                            ChatPanel.renderBubbleHtml("moderator", "Hi"));
                }

                @Test
                void renderBubbleHtmlEmptyAndNullContent() {
                    assertEquals("<html><body>You: </body></html>",
                            ChatPanel.renderBubbleHtml("user", null));
                    assertEquals("<html><body>You: </body></html>",
                            ChatPanel.renderBubbleHtml("USER", ""));
                    assertEquals("<html><body>Assistant: </body></html>",
                            ChatPanel.renderBubbleHtml("assistant", ""));
                }

                @Test
                void renderBubbleHtmlRendersBold() {
                    assertTrue(ChatPanel.renderBubbleHtml("assistant", "**bold**")
                            .contains("<strong>bold</strong>"));
                }

                @Test
                void renderBubbleHtmlWrapsParagraphs() {
                    String html = ChatPanel.renderBubbleHtml("user", "First\\n\\nSecond");
                    assertTrue(html.contains("<p>First</p>"));
                    assertTrue(html.contains("<p>Second</p>"));
                }

                @Test
                void renderStreamingAnswerRerendersAccumulatedText() {
                    assertEquals("<html><body>Assistant: Hello</body></html>",
                            ChatPanel.renderStreamingAnswer("Hello"));
                    assertTrue(ChatPanel.renderStreamingAnswer("**bold**")
                            .contains("<strong>bold</strong>"));
                    String html = ChatPanel.renderStreamingAnswer("First\\n\\nSecond");
                    assertTrue(html.contains("<p>First</p>"));
                    assertTrue(html.contains("<p>Second</p>"));
                }

                @Test
                void renderStreamingAnswerEmptyAndNull() {
                    assertEquals("<html><body>Assistant: </body></html>",
                            ChatPanel.renderStreamingAnswer(null));
                    assertEquals("<html><body>Assistant: </body></html>",
                            ChatPanel.renderStreamingAnswer(""));
                }
            }
            """;

    /** Full ChatPanelTest for the streaming-wiring step: keeps every existing assertion green and adds
     *  the two new deterministic RED tests for the accumulating answer buffer. */
    private static final String BLUEPRINT_STREAMING_WIRING_TEST = """
            package com.example.myplugin.ui;

            import com.example.myplugin.service.ConversationService;
            import org.junit.jupiter.api.Test;

            import static org.junit.jupiter.api.Assertions.*;

            class ChatPanelTest {

                private void flushEdt() {
                    try {
                        javax.swing.SwingUtilities.invokeAndWait(() -> { });
                    } catch (Exception ignored) {
                    }
                }

                @Test
                void stopDuringStreamingResetsUiState() {
                    ChatPanel panel = new ChatPanel(null, ConversationService.inMemory());
                    panel.simulateStreamingForTest();
                    flushEdt();
                    assertTrue(panel.isStreamingActive());
                    assertTrue(panel.isStopEnabled());
                    assertFalse(panel.isSendEnabled());

                    panel.stopStreaming();
                    flushEdt();

                    assertFalse(panel.isStreamingActive());
                    assertTrue(panel.isSendEnabled());
                    assertFalse(panel.isStopEnabled());
                }

                @Test
                void renderBubbleHtmlPrefixesByRole() {
                    assertEquals("<html><body>You: Hello</body></html>",
                            ChatPanel.renderBubbleHtml("USER", "Hello"));
                    assertEquals("<html><body>You: Hello</body></html>",
                            ChatPanel.renderBubbleHtml("user", "Hello"));
                    assertEquals("<html><body>Assistant: Hello</body></html>",
                            ChatPanel.renderBubbleHtml("assistant", "Hello"));
                }

                @Test
                void renderBubbleHtmlTreatsUnknownRoleAsAssistant() {
                    assertEquals("<html><body>Assistant: Hi</body></html>",
                            ChatPanel.renderBubbleHtml("moderator", "Hi"));
                }

                @Test
                void renderBubbleHtmlEmptyAndNullContent() {
                    assertEquals("<html><body>You: </body></html>",
                            ChatPanel.renderBubbleHtml("user", null));
                    assertEquals("<html><body>You: </body></html>",
                            ChatPanel.renderBubbleHtml("USER", ""));
                    assertEquals("<html><body>Assistant: </body></html>",
                            ChatPanel.renderBubbleHtml("assistant", ""));
                }

                @Test
                void renderBubbleHtmlRendersBold() {
                    assertTrue(ChatPanel.renderBubbleHtml("assistant", "**bold**")
                            .contains("<strong>bold</strong>"));
                }

                @Test
                void renderBubbleHtmlWrapsParagraphs() {
                    String html = ChatPanel.renderBubbleHtml("user", "First\\n\\nSecond");
                    assertTrue(html.contains("<p>First</p>"));
                    assertTrue(html.contains("<p>Second</p>"));
                }

                @Test
                void renderStreamingAnswerRerendersAccumulatedText() {
                    assertEquals("<html><body>Assistant: Hello</body></html>",
                            ChatPanel.renderStreamingAnswer("Hello"));
                    assertTrue(ChatPanel.renderStreamingAnswer("**bold**")
                            .contains("<strong>bold</strong>"));
                    String html = ChatPanel.renderStreamingAnswer("First\\n\\nSecond");
                    assertTrue(html.contains("<p>First</p>"));
                    assertTrue(html.contains("<p>Second</p>"));
                }

                @Test
                void renderStreamingAnswerEmptyAndNull() {
                    assertEquals("<html><body>Assistant: </body></html>",
                            ChatPanel.renderStreamingAnswer(null));
                    assertEquals("<html><body>Assistant: </body></html>",
                            ChatPanel.renderStreamingAnswer(""));
                }

                @Test
                void appendStreamingChunkAccumulatesAndRendersEachFragment() {
                    StringBuilder buffer = new StringBuilder();
                    assertEquals("<html><body>Assistant: Hel</body></html>",
                            ChatPanel.appendStreamingChunk("Hel", buffer));
                    assertEquals("<html><body>Assistant: Hello</body></html>",
                            ChatPanel.appendStreamingChunk("lo", buffer));
                    assertEquals("<html><body>Assistant: Hello</body></html>",
                            ChatPanel.appendStreamingChunk(null, buffer));
                }

                @Test
                void appendStreamingChunkRendersMarkdownAcrossFragments() {
                    StringBuilder buffer = new StringBuilder();
                    ChatPanel.appendStreamingChunk("**bo", buffer);
                    assertTrue(ChatPanel.appendStreamingChunk("ld**", buffer)
                            .contains("<strong>bold</strong>"));
                }
            }
            """;

    private static final List<TddBlueprint> BLUEPRINTS = List.of(
            new TddBlueprint("renderbubblehtml",
                    "src/test/java/com/example/myplugin/ui/ChatPanelTest.java",
                    BLUEPRINT_BUBBLE_TEST,
                    List.of(new TddBlueprint.Edit(
                            "src/main/java/com/example/myplugin/ui/ChatPanel.java",
                            "    // --- Test seams (package-private, deterministic RED target) ---\n",
                            "    static String renderBubbleHtml(String role, String content) {\n"
                                    + "        String prefix = role != null && role.equalsIgnoreCase(\"user\") ? \"You: \" : \"Assistant: \";\n"
                                    + "        String body = new com.example.markdown.MarkdownRenderer().render(content);\n"
                                    + "        return \"<html><body>\" + prefix + body + \"</body></html>\";\n"
                                    + "    }\n"
                                    + "\n"
                                    + "    // --- Test seams (package-private, deterministic RED target) ---\n")),
                    "src/main/java/com/example/myplugin/ui/ChatPanel.java",
                    new String[]{"renderBubbleHtml", "addUserMessage", "addAssistantMessage",
                            "currentAnswerArea", "setStreaming"}),
            new TddBlueprint("流式回答区",
                    "src/test/java/com/example/myplugin/ui/ChatPanelTest.java",
                    BLUEPRINT_STREAMING_TEST,
                    List.of(new TddBlueprint.Edit(
                            "src/main/java/com/example/myplugin/ui/ChatPanel.java",
                            "    // --- Test seams (package-private, deterministic RED target) ---\n",
                            "    static String renderStreamingAnswer(String accumulatedText) {\n"
                                    + "        return renderBubbleHtml(\"assistant\", accumulatedText);\n"
                                    + "    }\n"
                                    + "\n"
                                    + "    // --- Test seams (package-private, deterministic RED target) ---\n")),
                    "src/main/java/com/example/myplugin/ui/ChatPanel.java",
                    new String[]{"renderStreamingAnswer", "renderBubbleHtml", "currentAnswerArea",
                            "answerArea", "setStreaming", "isStreaming"}),
            new TddBlueprint("jtextpane",
                    "src/test/java/com/example/myplugin/ui/ChatPanelTest.java",
                    BLUEPRINT_STREAMING_WIRING_TEST,
                    List.of(
                            new TddBlueprint.Edit(
                                    "src/main/java/com/example/myplugin/ui/ChatPanel.java",
                                    "    // Streaming state\n"
                                            + "    private ThinkingPanel currentThinkingPanel;\n"
                                            + "    private JTextArea currentAnswerArea;\n",
                                    "    // Streaming state\n"
                                            + "    private ThinkingPanel currentThinkingPanel;\n"
                                            + "    private JTextPane currentAnswerArea;\n"
                                            + "    private final StringBuilder streamingAnswerBuffer = new StringBuilder();\n"),
                            new TddBlueprint.Edit(
                                    "src/main/java/com/example/myplugin/ui/ChatPanel.java",
                                    "        currentAnswerArea = new JTextArea(\"Assistant:\\n\");\n"
                                            + "        currentAnswerArea.setEditable(false);\n"
                                            + "        currentAnswerArea.setLineWrap(true);\n"
                                            + "        currentAnswerArea.setWrapStyleWord(true);\n"
                                            + "        currentAnswerArea.setFont(new Font(\"Monospaced\", Font.PLAIN, 13));\n",
                                    "        streamingAnswerBuffer.setLength(0);\n"
                                            + "        currentAnswerArea = new JTextPane();\n"
                                            + "        currentAnswerArea.setEditable(false);\n"
                                            + "        currentAnswerArea.setContentType(\"text/html\");\n"
                                            + "        currentAnswerArea.setFont(new Font(\"Monospaced\", Font.PLAIN, 13));\n"),
                            new TddBlueprint.Edit(
                                    "src/main/java/com/example/myplugin/ui/ChatPanel.java",
                                    "    static String renderStreamingAnswer(String accumulatedText) {\n"
                                            + "        return renderBubbleHtml(\"assistant\", accumulatedText);\n"
                                            + "    }\n"
                                            + "\n"
                                            + "    // --- Test seams (package-private, deterministic RED target) ---\n",
                                    "    static String renderStreamingAnswer(String accumulatedText) {\n"
                                            + "        return renderBubbleHtml(\"assistant\", accumulatedText);\n"
                                            + "    }\n"
                                            + "\n"
                                            + "    static String appendStreamingChunk(String chunk, StringBuilder buffer) {\n"
                                            + "        if (chunk != null) {\n"
                                            + "            buffer.append(chunk);\n"
                                            + "        }\n"
                                            + "        return renderStreamingAnswer(buffer.toString());\n"
                                            + "    }\n"
                                            + "\n"
                                            + "    // --- Test seams (package-private, deterministic RED target) ---\n"),
                            new TddBlueprint.Edit(
                                    "src/main/java/com/example/myplugin/ui/ChatPanel.java",
                                    "                        } else {\n"
                                            + "                            currentAnswerArea.append(text);\n"
                                            + "                            scrollToBottom(scrollPane);\n"
                                            + "                        }\n",
                                    "                        } else {\n"
                                            + "                            currentAnswerArea.setText(appendStreamingChunk(text, streamingAnswerBuffer));\n"
                                            + "                            scrollToBottom(scrollPane);\n"
                                            + "                        }\n"),
                            new TddBlueprint.Edit(
                                    "src/main/java/com/example/myplugin/ui/ChatPanel.java",
                                    "                        currentAnswerArea.append(\"\\n\\nError: \" + errorMsg);\n",
                                    "                        currentAnswerArea.setText(renderStreamingAnswer(streamingAnswerBuffer.toString() + \"\\n\\nError: \" + errorMsg));\n"),
                            new TddBlueprint.Edit(
                                    "src/main/java/com/example/myplugin/ui/ChatPanel.java",
                                    "                    currentAnswerArea.append(\"\\n\\nError: \" + e.getMessage());\n",
                                    "                    currentAnswerArea.setText(renderStreamingAnswer(streamingAnswerBuffer.toString() + \"\\n\\nError: \" + e.getMessage()));\n")),
                    "src/main/java/com/example/myplugin/ui/ChatPanel.java",
                    new String[]{"appendStreamingChunk", "streamingAnswerBuffer", "currentAnswerArea",
                            "renderStreamingAnswer", "chatResponse", "streamResponse"}),
            new TddBlueprint("stop",
                    "src/test/java/com/example/myplugin/ui/ChatPanelTest.java",
                    BLUEPRINT_STOP_TEST,
                    List.of(new TddBlueprint.Edit(
                            "src/main/java/com/example/myplugin/ui/ChatPanel.java",
                            "        if (currentStreamThread != null) {\n"
                                    + "            currentStreamThread.interrupt();\n"
                                    + "            setStreaming(false);\n"
                                    + "        }\n",
                            "        if (currentStreamThread != null) {\n"
                                    + "            currentStreamThread.interrupt();\n"
                                    + "        }\n"
                                    + "        setStreaming(false);\n")),
                    "src/main/java/com/example/myplugin/ui/ChatPanel.java",
                    new String[]{"isStreaming", "stopStreaming", "setStreaming", "stopButton",
                            "sendButton", "currentStream", "agentThread", "stream", "Stream", "cancel"}));

    /** Informational tail for self-authored tasks: states the ground rules (real test output is the
     *  only evidence; finish with mark_refactor) but imposes no tool restrictions and no forced call
     *  sequence. Root-cause fixes for self-authored runs live in the tools (TestAnalyzer, enforcer,
     *  exploration guard), not in this text. */
    private static final String SELF_AUTHORED_TAIL =
            "This is a code-writing execution task, not an analysis or review task. "
            + "Work as an autonomous engineer in this repository using the tools provided.\n"
            + "Ground rules for this run:\n"
            + "1. The only task is the one from your instructions / the start_cycle task text. "
            + "Ignore unrelated ideas, older plan documents, and other pipeline topics.\n"
            + "2. Evidence is real only: the output you pass to verify_test and verify_code must be the "
            + "actual current result of a run_tests/run_gradle_task call you performed in this run. "
            + "Do not reuse, paraphrase, or imagine output.\n"
            + "3. Drive the TDD cycle with tdd_enforcer (start_cycle -> RED -> GREEN -> REFACTOR -> "
            + "mark_refactor). The enforcer's replies tell you which action is legal next; when a call "
            + "is rejected, read its reply and retry accordingly instead of repeating the same call.\n"
            + "4. Do not give a final answer until tdd_enforcer mark_refactor has succeeded and the "
            + "pipeline has advanced. Even if the step looks already implemented, complete the cycle so "
            + "the pipeline records verifiable evidence.";

    public PipelinePanel(Project project, ConversationService conversationService) {
        super(new BorderLayout());
        this.project = project;
        this.conversationService = conversationService;
        this.pipelineService = new PipelineService(project);
        AgentContext context = new AgentContext(project);
        SDDPipelineTool pipelineTool = new SDDPipelineTool(context, pipelineService);
        ExportPlanTool exportPlanTool = new ExportPlanTool(context);
        CreateChangeTool createChangeTool = new CreateChangeTool(context);
        this.agentExecutor = new AgentExecutor(project);
        this.toolRunner = (toolName, args) -> {
            AgentTool tool = switch (toolName) {
                case "sdd_pipeline" -> pipelineTool;
                case "export_plan" -> exportPlanTool;
                case "create_change" -> createChangeTool;
                default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
            };
            return tool.execute(args);
        };
        initUi();
    }

    /** Test constructor: no real Project required, tools are faked via the runner. */
    PipelinePanel(PipelineService pipelineService, ToolRunner toolRunner) {
        super(new BorderLayout());
        this.project = null;
        this.conversationService = null;
        this.pipelineService = pipelineService;
        this.toolRunner = toolRunner;
        initUi();
    }

    private void initUi() {
        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        add(buildToolbar(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);

        buildActionMenu();
        JLabel hint = new JLabel("Right-click a pipeline record for actions. Add Steps / Create Change (manual) use the Chat panel text channel.");
        hint.setFont(new Font("Dialog", Font.PLAIN, 11));
        hint.setForeground(new Color(130, 130, 150));
        hint.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));
        add(hint, BorderLayout.SOUTH);

        instanceTree.setCellRenderer(new InstanceRenderer());
        instanceTree.setRootVisible(false);
        instanceTree.addTreeSelectionListener(e -> refreshSummaryForSelection());
        instanceTree.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                maybeShowPopup(e);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                maybeShowPopup(e);
            }

            private void maybeShowPopup(java.awt.event.MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                javax.swing.tree.TreePath path = instanceTree.getPathForLocation(e.getX(), e.getY());
                if (path != null) {
                    instanceTree.setSelectionPath(path);
                }
                refreshSummaryForSelection();
                actionMenu.show(instanceTree, e.getX(), e.getY());
            }
        });
        refreshInstances();
    }

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new BorderLayout());
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refreshInstances());
        JPanel topRow = new JPanel(new BorderLayout());
        topRow.add(new JLabel(" Pipeline instances:"), BorderLayout.WEST);
        topRow.add(refreshButton, BorderLayout.EAST);
        bar.add(topRow, BorderLayout.NORTH);

        summaryLabel.setFont(new Font("Dialog", Font.ITALIC, 11));
        summaryLabel.setForeground(new Color(100, 100, 120));
        summaryLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        bar.add(summaryLabel, BorderLayout.CENTER);
        return bar;
    }

    private JComponent buildCenter() {
        JPanel treePanel = new JPanel(new BorderLayout());
        treePanel.add(new JBScrollPane(instanceTree), BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout());
        right.add(new JBScrollPane(output), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treePanel, right);
        split.setResizeWeight(0.45);
        split.setDividerLocation(260);
        return split;
    }

private void buildActionMenu() {
        actionMenu.add(advancePlanItem);
        actionMenu.add(startTddItem);
        actionMenu.add(executeStepItem);
        actionMenu.add(completeItem);
        actionMenu.addSeparator();
        actionMenu.add(exportPlanItem);
        actionMenu.add(statusItem);
        actionMenu.add(listItem);
        actionMenu.addSeparator();
        actionMenu.add(createChangeItem);
        actionMenu.add(linkChangeItem);

        bind(advancePlanItem, this::actionAdvancePlan);
        bind(startTddItem, this::actionStartTdd);
        bind(executeStepItem, this::actionExecuteStep);
        bind(completeItem, this::actionComplete);
        bind(exportPlanItem, this::actionExportPlan);
        bind(statusItem, this::actionStatus);
        bind(listItem, this::actionList);
        bind(createChangeItem, () -> disabledManual("Create Change"));
        bind(linkChangeItem, this::actionLinkChange);
    }

    private void bind(JMenuItem item, Runnable action) {
        item.addActionListener(e -> run(action));
    }

    private void run(Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            appendOutput("Error: " + ex.getMessage());
        }
    }

    private void refreshInstances() {
        pipelineService.reload();
        java.util.List<SpecPipelineInstance> all = pipelineService.getAll();
        instances = all.toArray(new SpecPipelineInstance[0]);
        instanceModel.clear();
        treeRoot.removeAllChildren();
        java.util.List<SpecPipelineInstance> active = new java.util.ArrayList<>();
        java.util.List<SpecPipelineInstance> done = new java.util.ArrayList<>();
        for (SpecPipelineInstance inst : all) {
            if (inst.getCurrentStage() == PipelineStage.DONE) {
                done.add(inst);
            } else {
                active.add(inst);
            }
            instanceModel.addElement(displayLabel(inst));
        }
        if (!active.isEmpty()) {
            treeRoot.add(buildGroupNode("Active (" + active.size() + ")", active));
        }
        if (!done.isEmpty()) {
            treeRoot.add(buildGroupNode("Completed (" + done.size() + ")", done));
        }
        ((javax.swing.tree.DefaultTreeModel) instanceTree.getModel()).reload();
        selectFirstInstance();
        refreshSummaryForSelection();
    }

    private DefaultMutableTreeNode buildGroupNode(String title, java.util.List<SpecPipelineInstance> group) {
        DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(title);
        for (SpecPipelineInstance inst : group) {
            DefaultMutableTreeNode instNode = new DefaultMutableTreeNode(inst);
            java.util.List<String> steps = inst.getPlanSteps();
            if (steps == null || steps.isEmpty()) {
                instNode.add(new DefaultMutableTreeNode("(no plan steps yet \u2014 use Add in the Chat panel)"));
            } else {
                for (int i = 0; i < steps.size(); i++) {
                    String marker = (i == inst.getCurrentStepIndex()) ? ">> " : "   ";
                    instNode.add(new DefaultMutableTreeNode(marker + (i + 1) + ". " + steps.get(i)));
                }
            }
            groupNode.add(instNode);
        }
        return groupNode;
    }

    void selectFirstInstance() {
        DefaultMutableTreeNode first = firstInstanceNode();
        if (first == null) {
            return;
        }
        javax.swing.tree.TreePath path = new javax.swing.tree.TreePath(first.getPath());
        instanceTree.expandPath(path.getParentPath());
        instanceTree.setSelectionPath(path);
    }

    private DefaultMutableTreeNode firstInstanceNode() {
        java.util.Enumeration<?> e = treeRoot.depthFirstEnumeration();
        while (e.hasMoreElements()) {
            Object obj = e.nextElement();
            if (!(obj instanceof DefaultMutableTreeNode node)) continue;
            if (node.getUserObject() instanceof SpecPipelineInstance) {
                return node;
            }
        }
        return null;
    }

    private String displayLabel(SpecPipelineInstance inst) {
        String req = inst.getRequirement();
        String shortReq = req != null && req.length() > 30 ? req.substring(0, 30) + "..." : (req != null ? req : "");
        String domain = inst.getSpecDomain() != null && !inst.getSpecDomain().isEmpty()
                ? inst.getSpecDomain() : "no-spec";
        String change = inst.getChangeName() != null && !inst.getChangeName().isEmpty()
                ? " | change: " + inst.getChangeName() : "";
        return "[" + inst.getCurrentStage() + "] " + shortReq + "  (" + domain + ")" + change;
    }

    private void refreshSummaryForSelection() {
        SpecPipelineInstance selected = selectedInstance();
        if (selected == null) {
            summaryLabel.setText("No active pipeline instance selected.");
            enableButtons(false);
            return;
        }
        String req = selected.getRequirement();
        String shortReq = req != null && req.length() > 40 ? req.substring(0, 40) + "..." : (req != null ? req : "");
        summaryLabel.setText("[" + selected.getCurrentStage() + "] step "
                + (selected.getCurrentStepIndex() + 1) + "/" + selected.getPlanSteps().size()
                + " \u00b7 " + shortReq);
        enableButtons(true);
    }

    private SpecPipelineInstance selectedInstance() {
        if (instanceTree.isSelectionEmpty()) {
            return null;
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) instanceTree.getLastSelectedPathComponent();
        while (node != null && node != treeRoot) {
            Object user = node.getUserObject();
            if (user instanceof SpecPipelineInstance inst) {
                return inst;
            }
            node = (DefaultMutableTreeNode) node.getParent();
        }
        return null;
    }

    private void enableButtons(boolean hasSelection) {
        SpecPipelineInstance selected = selectedInstance();
        advancePlanItem.setEnabled(hasSelection && selected != null && selected.getCurrentStage() == PipelineStage.SPEC);
        startTddItem.setEnabled(hasSelection && selected != null
                && (selected.getCurrentStage() == PipelineStage.PLAN || selected.getCurrentStage() == PipelineStage.TDD));
        executeStepItem.setEnabled(hasSelection && selected != null
                && selected.getCurrentStage() == PipelineStage.TDD
                && selected.getCurrentStepIndex() >= 0
                && selected.getCurrentStepIndex() < selected.getPlanSteps().size());
        completeItem.setEnabled(hasSelection && selected != null && selected.getCurrentStage() == PipelineStage.CHANGE);
        exportPlanItem.setEnabled(true);
        statusItem.setEnabled(hasSelection);
        listItem.setEnabled(true);
        createChangeItem.setEnabled(true);
        linkChangeItem.setEnabled(hasSelection);
    }

    private void disabledManual(String name) {
        appendOutput(name + " requires manual parameters and is not available in this panel.\n"
                + "Use the text command channel instead (e.g. 'pipeline start ...', 'change create ...').");
    }

    private void actionLinkChange() {
        SpecPipelineInstance inst = requireSelected();
        if (inst == null) return;
        String current = inst.getChangeName();
        String name = changeNamePrompt.apply(current != null && !current.isEmpty() ? current : "stop-button-cancel");
        if (name == null || name.trim().isEmpty()) return;
        name = name.trim();
        JsonObject args = new JsonObject();
        args.addProperty("action", "link_change");
        args.addProperty("pipeline_id", inst.getId());
        args.addProperty("change_name", name);
        invoke("sdd_pipeline", args);
    }

    private String promptChangeName(String initial) {
        return (String) JOptionPane.showInputDialog(
                this,
                "Change name (openspec/changes/<name>):",
                "Link Change",
                JOptionPane.PLAIN_MESSAGE,
                null, null, initial);
    }

    private void actionAdvancePlan() {
        SpecPipelineInstance inst = requireSelected();
        if (inst == null) return;
        JsonObject args = new JsonObject();
        args.addProperty("action", "advance_plan");
        args.addProperty("pipeline_id", inst.getId());
        invoke("sdd_pipeline", args);
    }

    private void actionStartTdd() {
        SpecPipelineInstance inst = requireSelected();
        if (inst == null) return;
        if (inst.getPlanSteps().isEmpty()) {
            appendOutput("No plan steps. Use Add Steps via the text command channel first.");
            return;
        }
        int next = inst.getCurrentStepIndex() < 0 ? 0 : inst.getCurrentStepIndex() + 1;
        if (inst.getCurrentStage() == PipelineStage.PLAN) {
            next = 0;
        }
        if (next >= inst.getPlanSteps().size()) {
            appendOutput("All plan steps already started.");
            return;
        }
        JsonObject args = new JsonObject();
        args.addProperty("action", "start_tdd");
        args.addProperty("pipeline_id", inst.getId());
        args.addProperty("step_index", next);
        invoke("sdd_pipeline", args);
    }

    private void actionComplete() {
        SpecPipelineInstance inst = requireSelected();
        if (inst == null) return;
        JsonObject args = new JsonObject();
        args.addProperty("action", "complete");
        args.addProperty("pipeline_id", inst.getId());
        invoke("sdd_pipeline", args);
    }

    private void actionExecuteStep() {
        if (agentExecutor == null) {
            appendOutput("Agent executor not available.");
            return;
        }
        SpecPipelineInstance inst = requireSelected();
        if (inst == null) return;
        if (inst.getCurrentStepIndex() < 0) {
            appendOutput("No active TDD step. Click Start TDD first.");
            return;
        }
        if (isVerifyStep(inst)) {
            runVerifyStep(inst);
            return;
        }
        String modelName = resolveModelName();
        if (modelName == null) {
            appendOutput("No loaded model. Select a model in the Chat panel first.");
            return;
        }
        String pipelineId = inst.getId();
        String task = inst.currentStepTddTask();
        String tddState = agentExecutor.currentTddState();
        String linkedPipelineId = agentExecutor.tddLinkedPipelineId();
        String cycleTask = agentExecutor.tddCurrentTask();
        boolean resume = !shouldResetTdd(tddState, linkedPipelineId, pipelineId, cycleTask, task);
        String instruction;
        TddBlueprint blueprint;
        String tddTail;
        String finalInstruction;
        if (resume) {
            blueprint = null;
            tddTail = SELF_AUTHORED_TAIL;
            finalInstruction = buildResumeInstruction(pipelineId,
                    inst.getCurrentStepIndex() + 1, inst.getPlanSteps().size(), tddState);
        } else {
            instruction = "Execute the TDD cycle for pipeline " + pipelineId
                    + " (current step: " + (inst.getCurrentStepIndex() + 1) + "/" + inst.getPlanSteps().size() + ").\n"
                    + "Run tdd_enforcer start_cycle with pipeline_id=" + pipelineId + " and task=\"" + task + "\".\n"
                    + "Then run RED->GREEN->REFACTOR: write a failing test, run it and report the output "
                    + "(verify_test), write minimal code, run the tests and report output (verify_code), "
                    + "refactor, then mark_refactor to advance the pipeline.";
            blueprint = BLUEPRINTS.stream()
                    .filter(b -> b.matches(task))
                    .findFirst()
                    .orElse(null);
            if (blueprint != null) {
                tddTail = blueprintTail(blueprint);
                String sourceContext = extractSourceContext(blueprint.sourceContextFile(),
                        blueprint.sourceContextKeywords());
                finalInstruction = (sourceContext.isEmpty() ? instruction : instruction + "\n\n" + sourceContext)
                        + "\n\nDETERMINISTIC RED TEST \u2014 write this test file exactly as given below:\n"
                        + "File: " + blueprint.testFilePath() + "\n" + blueprint.testSource();
            } else {
                tddTail = SELF_AUTHORED_TAIL;
                finalInstruction = instruction + "\n\nDesign and write the failing test yourself, then implement "
                        + "minimal code to make it pass. Follow the self-authored TDD workflow steps in the "
                        + "system prompt tail exactly.";
            }
        }
        appendOutput((resume ? "Resuming TDD step for " + pipelineId + " (state: " + tddState + ")" : "Executing TDD step for " + pipelineId) + " ...");
        new Thread(() -> {
            try {
                List<com.example.myplugin.model.Conversation.ChatMessage> history = new java.util.ArrayList<>();
                history.add(new com.example.myplugin.model.Conversation.ChatMessage(
                        com.example.myplugin.model.Conversation.Role.USER, finalInstruction));
                int toolLimit = (blueprint != null) ? 32 : 48;
                agentExecutor.setToolCallLimitOverride(toolLimit);
                agentExecutor.setMaxIterationsOverride(30);
                agentExecutor.setRoleOverride("dev");
                agentExecutor.setSystemPromptTail(tddTail);
                agentExecutor.setSuppressMemoryInjection(true);
                if (blueprint != null) {
                    java.util.List<AgentExecutor.RequiredEdit> requiredEdits = new java.util.ArrayList<>();
                    for (TddBlueprint.Edit e : blueprint.edits()) {
                        requiredEdits.add(new AgentExecutor.RequiredEdit(e.filePath(), e.oldString(), e.newString()));
                    }
                    agentExecutor.setRequiredEdits(requiredEdits);
                } else {
                    agentExecutor.setRequiredEdits(null);
                }
                if (resume) {
                    SwingUtilities.invokeLater(() -> appendOutput("[Resume] cycle already at " + tddState
                            + " for " + pipelineId + "; skipping reset"));
                } else {
                    String reset = agentExecutor.runTool("tdd_enforcer",
                            "{\"action\":\"cancel\"}");
                    SwingUtilities.invokeLater(() -> appendOutput("[Reset] " + reset));
                }
                String answer = agentExecutor.execute(history, modelName, new AgentExecutor.AgentEvent() {
                    @Override
                    public void onThinking(String text) {
                        SwingUtilities.invokeLater(() -> appendOutput("[Thinking] " + text));
                    }

                    @Override
                    public void onToolCall(String toolName, String arguments) {
                        SwingUtilities.invokeLater(() -> appendOutput("[Tool] " + toolName + " " + arguments));
                    }

                    @Override
                    public void onToolResult(String toolName, String result) {
                        SwingUtilities.invokeLater(() -> appendOutput("[Result] " + result));
                    }

                    @Override
                    public void onAnswer(String text) {
                        if (text != null) {
                            SwingUtilities.invokeLater(() -> appendOutput(text));
                        }
                    }

                    @Override
                    public void onError(String error) {
                        SwingUtilities.invokeLater(() -> appendOutput("[Error] " + error));
                    }
                });
                SwingUtilities.invokeLater(() -> {
                    appendOutput("=== Done ===\n" + answer);
                    refreshInstances();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> appendOutput("Execution failed: " + ex.getMessage()));
            }
        }).start();
    }

    /** True when the selected pipeline's current plan step is a verification/regression gate
     *  (kind "verify"): it runs the full suite instead of a RED->GREEN->REFACTOR cycle. */
    static boolean isVerifyStep(SpecPipelineInstance inst) {
        return inst != null && inst.getCurrentStepIndex() >= 0
                && inst.isVerifyStep(inst.getCurrentStepIndex());
    }

    /** Runs the full regression suite for a verify step; on green, completes the step and advances. */
    private void runVerifyStep(SpecPipelineInstance inst) {
        appendOutput("Executing verify/regression step for " + inst.getId() + " ...");
        new Thread(() -> {
            String result = agentExecutor.runTool("run_tests", "{}");
            boolean passed = result != null && result.startsWith("TESTS PASSED");
            SwingUtilities.invokeLater(() -> {
                appendOutput("[Verify] " + result);
                if (passed) {
                    appendOutput(completeVerifyStep(inst));
                } else {
                    appendOutput("REGRESSION FAILED: the full test suite is not green; verify step not advanced.");
                }
                refreshInstances();
            });
        }).start();
    }

    /** Completes the current verify step and advances the pipeline (to the next step, or CHANGE when
     *  it is the last). Package-private so tests can drive it with an in-memory service. */
    String completeVerifyStep(SpecPipelineInstance inst) {
        int stepIndex = inst.getCurrentStepIndex();
        pipelineService.completeStepTdd(inst.getId(), stepIndex);
        SpecPipelineInstance after = pipelineService.getById(inst.getId());
        if (after != null && after.allStepsDone()) {
            pipelineService.advance(inst.getId(), PipelineStage.CHANGE, "all plan steps done (final verify)");
            return "Verify step " + (stepIndex + 1) + " passed; all plan steps done -> CHANGE.";
        }
        int next = stepIndex + 1;
        if (after != null && after.getPlanSteps().size() > next) {
            pipelineService.startStepTdd(inst.getId(), next);
            return "Verify step " + (stepIndex + 1) + " passed; advanced to step " + (next + 1) + ".";
        }
        return "Verify step " + (stepIndex + 1) + " passed.";
    }

    private String extractSourceContext(String sourceContextFile, String[] keywords) {
        if (project == null) return "";
        String basePath = project.getBasePath();
        if (basePath == null) return "";
        try {
            java.io.File f = new java.io.File(basePath, sourceContextFile);
            if (!f.exists()) return "";
            java.util.List<String> lines = java.nio.file.Files.readAllLines(f.toPath());
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) {
                if (out.length() > 9000) break;
                String line = lines.get(i);
                if (!containsAny(line, keywords)) continue;
                String trimmed = line.trim();
                if (trimmed.endsWith("{") && trimmed.contains("(") && trimmed.contains(")")) {
                    out.append(line).append("\n");
                    int depth = 1;
                    int j = i;
                    while (depth > 0 && j + 1 < lines.size() && out.length() <= 9000) {
                        j++;
                        String l = lines.get(j);
                        out.append(l).append("\n");
                        depth += openBraces(l) - closeBraces(l);
                    }
                    i = j;
                } else {
                    out.append(line).append("\n");
                }
            }
            if (out.length() == 0) return "";
            return "\nRelevant source context (" + sourceContextFile + "): "
                    + "the current step task maps to a deterministic blueprint, so the relevant fields and "
                    + "methods are included inline here.\n" + out.toString();
        } catch (java.io.IOException e) {
            return "";
        }
    }

    private static String blueprintTail(TddBlueprint b) {
        StringBuilder sb = new StringBuilder();
        sb.append("THIS IS A CODE-WRITING EXECUTION TASK, NOT AN ANALYSIS OR REVIEW TASK. "
                + "You MUST actually create and modify files, never just analyze. You already have a "
                + "completely compilable failing test below, so you do NOT need to explore anything: "
                + "do NOT call read_file, list_files, search_code, or execute_command at all. "
                + "Ignore any instruction to stop after 2-3 tool calls: you have a large tool-call budget, "
                + "so keep calling tools until the TDD cycle is complete. "
                + "The ONLY task is the one in your instructions from this conversation. IGNORE all other "
                + "ideas or reminders: there is no TDDPipelineBridge, no pipeline 'link' action, no other "
                + "pipeline. Do NOT call tdd_enforcer cancel. Do NOT call tdd_enforcer start_cycle more "
                + "than once, and only with the exact task text from step 1. "
                + "Follow this exact step order:\n"
                + "1. tdd_enforcer start_cycle with pipeline_id and task.\n"
                + "2. RED: write_file the deterministic test verbatim (byte-for-byte) to "
                + b.testFilePath() + ". Do NOT change a single character, do NOT create any other file.\n"
                + "3. Run the test with run_tests. Test fails at RED (this is expected). "
                + "Then call tdd_enforcer write_test, and immediately after it call "
                + "tdd_enforcer verify_test with the real run output.\n");
        List<TddBlueprint.Edit> edits = b.edits();
        if (edits.size() == 1) {
            TddBlueprint.Edit e = edits.get(0);
            sb.append("4. GREEN: call edit_file once on ").append(e.filePath())
                    .append(" with EXACTLY these values:\n")
                    .append("   old_string:\n").append(e.oldString())
                    .append("   new_string:\n").append(e.newString())
                    .append("   Do not change any other part of the file. ");
        } else {
            sb.append("4. GREEN: apply EXACTLY these ").append(edits.size())
                    .append(" edits with edit_file, ONE call per edit, IN THIS ORDER. ")
                    .append("Use each old_string and new_string byte-for-byte and do not change any other "
                            + "part of any file. Apply each edit only once. If edit_file reports that an "
                            + "old_string was not found, that edit was already applied: do not retry it, "
                            + "just continue with the next edit.\n");
            int i = 1;
            for (TddBlueprint.Edit e : edits) {
                sb.append("   edit_file ").append(i++).append(") file: ").append(e.filePath()).append("\n")
                        .append("      old_string:\n").append(e.oldString())
                        .append("      new_string:\n").append(e.newString()).append("\n");
            }
        }
        sb.append("Then call run_tests again, and call tdd_enforcer verify_code with the real output.\n"
                + "5. REFACTOR: refine if needed, then call tdd_enforcer mark_refactor.\n"
                + "Do NOT give a final text answer until tdd_enforcer mark_refactor has succeeded.");
        return sb.toString();
    }

    private static boolean containsAny(String s, String[] keywords) {
        for (String k : keywords) {
            if (s.contains(k)) return true;
        }
        return false;
    }

    private static int openBraces(String s) {
        int c = 0;
        for (char ch : s.toCharArray()) {
            if (ch == '{') c++;
        }
        return c;
    }

    private static int closeBraces(String s) {
        int c = 0;
        for (char ch : s.toCharArray()) {
            if (ch == '}') c++;
        }
        return c;
    }

    static String buildResumeInstruction(String pipelineId, int stepNumber, int totalSteps, String tddState) {
        return "RESUME the TDD cycle for pipeline " + pipelineId
                + " (current step: " + stepNumber + "/" + totalSteps + ").\n"
                + "A TDD cycle is already in progress for this pipeline (enforcer state: " + tddState + "). "
                + "Do NOT call start_cycle (it will be rejected) and do NOT call cancel.\n"
                + "Continue the cycle from its current state: run the tests and pass their REAL current output "
                + "to tdd_enforcer (action=verify_code), then call action=mark_refactor when the enforcer "
                + "confirms the cycle is complete, so the pipeline records the step as done.";
    }

    /** Whether Execute Step should reset the TDD enforcer before the run. Mid-cycle states linked to the
     *  selected pipeline are resumed instead; everything else (fresh, finished, blocked, or a cycle owned by
     *  another pipeline) is reset so the run starts from a clean state. A cycle whose task text does not match
     *  the selected plan step is also reset: it belongs to a different step, not the one being executed. */
    static boolean shouldResetTdd(String tddState, String linkedPipelineId, String selectedPipelineId,
                                  String cycleTask, String stepTask) {
        if (tddState == null || "IDLE".equals(tddState) || "DONE".equals(tddState) || "BLOCKED".equals(tddState)) {
            return true;
        }
        if (!selectedPipelineId.equals(linkedPipelineId)) {
            return true;
        }
        if (cycleTask != null && stepTask != null && !cycleTask.equals(stepTask)) {
            return true;
        }
        return false;
    }

    private String resolveModelName() {
        try {
            java.util.List<com.example.myplugin.model.LanguageModel> models =
                    com.example.myplugin.chatmodel.local.llamacpp.LlamaModelService.getInstance().getModels();
            if (models.isEmpty()) return null;
            for (com.example.myplugin.model.LanguageModel m : models) {
                if ("loaded".equals(m.getStatus())) return m.getModelName();
            }
            return models.get(0).getModelName();
        } catch (Exception e) {
            return null;
        }
    }

    private void actionExportPlan() {
        JsonObject args = new JsonObject();
        invoke("export_plan", args);
    }

    private void actionStatus() {
        SpecPipelineInstance inst = requireSelected();
        if (inst == null) return;
        JsonObject args = new JsonObject();
        args.addProperty("pipeline_id", inst.getId());
        invoke("sdd_pipeline", args);
    }

    private void actionList() {
        JsonObject args = new JsonObject();
        args.addProperty("action", "list");
        invoke("sdd_pipeline", args);
    }

    private SpecPipelineInstance requireSelected() {
        SpecPipelineInstance inst = selectedInstance();
        if (inst == null) {
            appendOutput("Select a pipeline instance first.");
        }
        return inst;
    }

    private void invoke(String toolName, JsonObject args) {
        String result = toolRunner.run(toolName, args);
        appendOutput(result);
        recordTrace(toolName, args.toString(), result);
        refreshInstances();
    }

    private void recordTrace(String toolName, String args, String result) {
        try {
            if (project != null && !project.isDisposed()) {
                TraceRecorder.record(project, toolName, args, result, "ui");
            } else {
                com.example.myplugin.model.Conversation conv = conversationService.getCurrentConversation();
                if (conv != null) {
                    conv.addTrace(toolName, args, result, "ui");
                    conversationService.save();
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void appendOutput(String text) {
        output.append(text);
        if (!text.isEmpty() && !text.endsWith("\n")) {
            output.append("\n");
        }
        output.append("-------------------------------\n");
        output.setCaretPosition(output.getDocument().getLength());
    }

    public void refreshStatus() {
        refreshInstances();
    }

    private class InstanceRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                      boolean expanded, boolean leaf, int row,
                                                      boolean hasFocus) {
            JLabel label = (JLabel) super.getTreeCellRendererComponent(
                    tree, value, sel, expanded, leaf, row, hasFocus);
            if (value instanceof DefaultMutableTreeNode node) {
                Object user = node.getUserObject();
                if (user instanceof SpecPipelineInstance inst) {
                    label.setText(displayLabel(inst));
                    label.setFont(label.getFont().deriveFont(Font.BOLD));
                    label.setToolTipText(formatTooltip(inst));
                }
            }
            return label;
        }
    }

    private String formatTooltip(SpecPipelineInstance inst) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html>ID: ").append(inst.getId()).append("<br>");
        sb.append("Stage: ").append(inst.getCurrentStage()).append("<br>");
        sb.append("Spec: ").append(inst.getSpecDomain() != null ? inst.getSpecDomain() : "-").append("<br>");
        sb.append("Requirement: ").append(inst.getRequirement() != null ? inst.getRequirement() : "").append("<br>");
        if (inst.getChangeName() != null && !inst.getChangeName().isEmpty()) {
            sb.append("Change: ").append(inst.getChangeName()).append("<br>");
        }
        sb.append("Steps: ").append(inst.getPlanSteps().size())
          .append(" (current ").append(inst.getCurrentStepIndex() + 1).append(")</html>");
        return sb.toString();
    }
}
