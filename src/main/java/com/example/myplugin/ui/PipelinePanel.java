package com.example.myplugin.ui;

import com.example.myplugin.agent.AgentContext;
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
import java.awt.*;

public class PipelinePanel extends JPanel {

    /** Test seam: executes a named tool, allowing unit tests to substitute a fake. */
    interface ToolRunner {
        String run(String toolName, JsonObject args);
    }

    private final Project project;
    private final ConversationService conversationService;
    private final PipelineService pipelineService;
    private final ToolRunner toolRunner;

    // Package-private so unit tests in com.example.myplugin.ui can inspect state.
    final DefaultListModel<String> instanceModel = new DefaultListModel<>();
    final JList<String> instanceList = new JList<>(instanceModel);
    final JLabel summaryLabel = new JLabel("No active pipeline instances.");
    final JTextArea output = new JTextArea(12, 40);

    final JButton advancePlanButton = new JButton("Advance Plan");
    final JButton startTddButton = new JButton("Start TDD");
    final JButton completeTddButton = new JButton("Complete TDD");
    final JButton completeButton = new JButton("Complete");
    final JButton exportPlanButton = new JButton("Export Plan");
    final JButton statusButton = new JButton("Status");
    final JButton listButton = new JButton("List");
    final JButton createChangeButton = new JButton("Create Change");
    final JButton linkChangeButton = new JButton("Link Change");

    private SpecPipelineInstance[] instances = new SpecPipelineInstance[0];

    public PipelinePanel(Project project, ConversationService conversationService) {
        super(new BorderLayout());
        this.project = project;
        this.conversationService = conversationService;
        this.pipelineService = new PipelineService(project);
        AgentContext context = new AgentContext(project);
        SDDPipelineTool pipelineTool = new SDDPipelineTool(context, pipelineService);
        ExportPlanTool exportPlanTool = new ExportPlanTool(context);
        CreateChangeTool createChangeTool = new CreateChangeTool(context);
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
        add(new JBScrollPane(output), BorderLayout.SOUTH);

        instanceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        instanceList.setCellRenderer(new InstanceRenderer());
        instanceList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                refreshSummaryForSelection();
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
        JPanel listPanel = new JPanel(new BorderLayout());
        listPanel.add(new JBScrollPane(instanceList), BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout(JBUI.insets(4).left, 4));
        right.add(setupButtons(), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listPanel, right);
        split.setResizeWeight(0.45);
        split.setDividerLocation(260);
        return split;
    }

    private JComponent setupButtons() {
        JPanel panel = new JPanel(new BorderLayout(JBUI.insets(4).left, 8));

        JPanel autoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        autoRow.add(enable(advancePlanButton, this::actionAdvancePlan));
        autoRow.add(enable(startTddButton, this::actionStartTdd));
        autoRow.add(enable(completeTddButton, this::actionCompleteTdd));
        autoRow.add(enable(completeButton, this::actionComplete));
        autoRow.add(enable(exportPlanButton, this::actionExportPlan));
        autoRow.add(enable(statusButton, this::actionStatus));
        autoRow.add(enable(listButton, this::actionList));
        panel.add(autoRow, BorderLayout.NORTH);

        JLabel hint = new JLabel("Start / Add Steps / Link Change need manual params \u2014 use the text command channel.");
        hint.setFont(new Font("Dialog", Font.PLAIN, 11));
        hint.setForeground(new Color(130, 130, 150));
        hint.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));
        panel.add(hint, BorderLayout.CENTER);

        JPanel manualRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        manualRow.add(enable(createChangeButton, () -> disabledManual("Create Change")));
        manualRow.add(enable(linkChangeButton, () -> disabledManual("Link Change")));
        panel.add(manualRow, BorderLayout.SOUTH);

        return panel;
    }

    private JButton enable(JButton button, Runnable action) {
        button.addActionListener(e -> run(action));
        return button;
    }

    private void run(Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            appendOutput("Error: " + ex.getMessage());
        }
    }

    private void refreshInstances() {
        java.util.List<SpecPipelineInstance> active = pipelineService.getActive();
        instances = active.toArray(new SpecPipelineInstance[0]);
        instanceModel.clear();
        for (SpecPipelineInstance inst : instances) {
            instanceModel.addElement(displayLabel(inst));
        }
        if (instanceModel.size() > 0) {
            instanceList.setSelectedIndex(0);
        }
        refreshSummaryForSelection();
    }

    private String displayLabel(SpecPipelineInstance inst) {
        String req = inst.getRequirement();
        String shortReq = req != null && req.length() > 30 ? req.substring(0, 30) + "..." : (req != null ? req : "");
        String domain = inst.getSpecDomain() != null && !inst.getSpecDomain().isEmpty()
                ? inst.getSpecDomain() : "no-spec";
        return "[" + inst.getCurrentStage() + "] " + shortReq + "  (" + domain + ")";
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
        int idx = instanceList.getSelectedIndex();
        return (idx >= 0 && idx < instances.length) ? instances[idx] : null;
    }

    private void enableButtons(boolean hasSelection) {
        SpecPipelineInstance selected = selectedInstance();
        advancePlanButton.setEnabled(hasSelection && selected != null && selected.getCurrentStage() == PipelineStage.SPEC);
        startTddButton.setEnabled(hasSelection && selected != null
                && (selected.getCurrentStage() == PipelineStage.PLAN || selected.getCurrentStage() == PipelineStage.TDD));
        completeTddButton.setEnabled(hasSelection && selected != null && selected.getCurrentStage() == PipelineStage.TDD);
        completeButton.setEnabled(hasSelection && selected != null && selected.getCurrentStage() == PipelineStage.CHANGE);
        exportPlanButton.setEnabled(true);
        statusButton.setEnabled(hasSelection);
        listButton.setEnabled(true);
        createChangeButton.setEnabled(true);
        linkChangeButton.setEnabled(hasSelection);
    }

    private void disabledManual(String name) {
        appendOutput(name + " requires manual parameters and is not available in this panel.\n"
                + "Use the text command channel instead (e.g. 'pipeline start ...', 'change create ...').");
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

    private void actionCompleteTdd() {
        SpecPipelineInstance inst = requireSelected();
        if (inst == null) return;
        JsonObject args = new JsonObject();
        args.addProperty("action", "complete_tdd");
        args.addProperty("pipeline_id", inst.getId());
        args.addProperty("step_index", inst.getCurrentStepIndex());
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

    private class InstanceRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            if (index >= 0 && index < instances.length) {
                label.setToolTipText(formatTooltip(instances[index]));
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
        sb.append("Steps: ").append(inst.getPlanSteps().size())
          .append(" (current ").append(inst.getCurrentStepIndex() + 1).append(")</html>");
        return sb.toString();
    }
}
