package com.example.myplugin.agent.plan;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentTool;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExportPlanTool implements AgentTool {

    private static final Pattern TASK_RE = Pattern.compile("^## Task\\s+(.+)$");
    private static final Pattern STEP_RE = Pattern.compile("^(?:#{3,6}\\s+|-\\s+|\\d+\\.\\s+)(.+)$");

    private final Path baseDir;
    private final PlanStore planStore;

    public ExportPlanTool(AgentContext context) {
        this.baseDir = context.getBaseDir();
        this.planStore = new PlanStore(context.getProject().getBasePath());
    }

    @Override
    public String name() { return "export_plan"; }

    @Override
    public String description() {
        return "Read plan documents under docs/plans and export them as Markdown to docs/plans/export. "
                + "Supply 'file' as the plan file name (e.g. 2026-08-28-sdd-tdd-pipeline-plan.md). "
                + "If omitted, the most recent plan is exported.";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
            .name(name())
            .description(description())
            .parameters(JsonObjectSchema.builder()
                .addStringProperty("file", "Plan file name under docs/plans (for export)")
                .build())
            .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        try {
            String planFile = arguments.has("file") ? arguments.get("file").getAsString() : null;
            if (planFile == null || planFile.trim().isEmpty()) {
                List<PlanStore.PlanInfo> plans = planStore.listPlans();
                if (plans.isEmpty()) {
                    return "Error: no plans in docs/plans. Use generate_plan first.";
                }
                planFile = plans.get(plans.size() - 1).getName();
            }

            String content = planStore.readPlan(planFile);
            if (content == null) {
                return "Error: Plan '" + planFile + "' not found";
            }

            String exportName = planFile.replaceAll("\\.md$", "") + "-export.md";
            Path exportDir = Paths.get(baseDir.toString(), "docs", "plans", "export");
            Files.createDirectories(exportDir);
            Path exportPath = exportDir.resolve(exportName);

            String rendered = render(content, planFile, exportName);
            Files.writeString(exportPath, rendered, StandardCharsets.UTF_8);

            StringBuilder sb = new StringBuilder();
            sb.append("=== Plan Exported ===\n");
            sb.append("Source: docs/plans/").append(planFile).append("\n");
            sb.append("Exported file: ").append(toRelative(exportPath)).append("\n\n");
            sb.append(truncate(rendered, 1200));
            return sb.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String render(String content, String planFile, String exportName) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(exportName.replaceAll("\\.md$", "")).append("\n\n");
        sb.append("> Exported from `docs/plans/").append(planFile).append("`\n\n");
        sb.append("---\n\n");
        sb.append("## Task/Step Summary\n\n");

        String currentTask = "Ungrouped";
        List<String> steps = new java.util.ArrayList<>();
        java.util.Map<String, java.util.List<String>> groups = new java.util.LinkedHashMap<>();
        groups.put(currentTask, steps);

        for (String line : content.split("\n")) {
            Matcher tm = TASK_RE.matcher(line);
            if (tm.matches()) {
                currentTask = tm.group(1).trim();
                groups.put(currentTask, new java.util.ArrayList<>());
                continue;
            }
            Matcher sm = STEP_RE.matcher(line);
            if (sm.matches()) {
                groups.get(currentTask).add(sm.group(1).trim());
            }
        }

        int taskCount = groups.size();
        int stepCount = groups.values().stream().mapToInt(java.util.List::size).sum();
        sb.append("Tasks: ").append(taskCount).append(", Steps: ").append(stepCount).append("\n\n");

        for (java.util.Map.Entry<String, java.util.List<String>> e : groups.entrySet()) {
            sb.append("## ").append(e.getKey()).append("\n\n");
            if (e.getValue().isEmpty()) {
                sb.append("- (no steps listed)\n");
            } else {
                for (String s : e.getValue()) {
                    sb.append("- ").append(s).append("\n");
                }
            }
            sb.append("\n");
        }

        sb.append("---\n\n");
        sb.append("## Full Plan Content\n\n");
        sb.append(content);
        if (!content.endsWith("\n")) sb.append("\n");
        return sb.toString();
    }

    private String toRelative(Path p) {
        Path rel = baseDir.relativize(p);
        return rel.toString().replace('\\', '/');
    }

    private String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "\n... (truncated)";
    }
}