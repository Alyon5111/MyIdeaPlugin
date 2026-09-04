package com.example.myplugin.agent.plan;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class PlanStore {

    private final String plansDir;

    public PlanStore(String projectPath) {
        this.plansDir = Paths.get(projectPath, "docs", "plans").toString();
    }

    public String getPlansDir() { return plansDir; }

    public void savePlan(String planContent, String fileName) throws Exception {
        Path dir = Paths.get(plansDir);
        Files.createDirectories(dir);
        Path planPath = dir.resolve(fileName);
        Files.writeString(planPath, planContent, StandardCharsets.UTF_8);
    }

    public List<PlanInfo> listPlans() throws Exception {
        List<PlanInfo> plans = new ArrayList<>();
        Path dir = Paths.get(plansDir);
        if (!Files.exists(dir)) return plans;

        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".md"))
                  .forEach(p -> {
                      String name = p.getFileName().toString();
                      String content;
                      try {
                          content = Files.readString(p, StandardCharsets.UTF_8);
                      } catch (Exception e) {
                          content = "";
                      }
                      int taskCount = countTasks(content);
                      int completedCount = countCompletedTasks(content);
                      plans.add(new PlanInfo(name, p.toString(), taskCount, completedCount));
                  });
        }
        return plans;
    }

    public String readPlan(String fileName) throws Exception {
        Path planPath = Paths.get(plansDir, fileName);
        if (!Files.exists(planPath)) return null;
        return Files.readString(planPath, StandardCharsets.UTF_8);
    }

    public boolean updateTaskStatus(String fileName, int taskIndex, int stepIndex, boolean completed) throws Exception {
        Path planPath = Paths.get(plansDir, fileName);
        if (!Files.exists(planPath)) return false;

        String content = Files.readString(planPath, StandardCharsets.UTF_8);
        String[] lines = content.split("\n");
        boolean updated = false;

        int currentTask = -1;
        int currentStep = -1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("## Task ")) {
                currentTask++;
                currentStep = -1;
            } else if (line.contains("- [ ]") || line.contains("- [x]")) {
                currentStep++;
                if (currentTask == taskIndex && currentStep == stepIndex) {
                    if (completed) {
                        lines[i] = line.replace("- [ ]", "- [x]");
                    } else {
                        lines[i] = line.replace("- [x]", "- [ ]");
                    }
                    updated = true;
                    break;
                }
            }
        }

        if (updated) {
            Files.writeString(planPath, String.join("\n", lines), StandardCharsets.UTF_8);
        }
        return updated;
    }

    private int countTasks(String content) {
        int count = 0;
        for (String line : content.split("\n")) {
            if (line.startsWith("## Task ")) count++;
        }
        return count;
    }

    private int countCompletedTasks(String content) {
        int count = 0;
        boolean inCompletedTask = false;
        for (String line : content.split("\n")) {
            if (line.startsWith("## Task ")) {
                inCompletedTask = true;
            } else if (inCompletedTask && line.startsWith("- [x]")) {
                count++;
                inCompletedTask = false;
            } else if (inCompletedTask && line.startsWith("- [ ]")) {
                inCompletedTask = false;
            }
        }
        return count;
    }

    public static class PlanInfo {
        private final String name;
        private final String path;
        private final int taskCount;
        private final int completedTasks;

        public PlanInfo(String name, String path, int taskCount, int completedTasks) {
            this.name = name;
            this.path = path;
            this.taskCount = taskCount;
            this.completedTasks = completedTasks;
        }

        public String getName() { return name; }
        public String getPath() { return path; }
        public int getTaskCount() { return taskCount; }
        public int getCompletedTasks() { return completedTasks; }
        public int getProgress() {
            return taskCount > 0 ? (completedTasks * 100 / taskCount) : 0;
        }
    }
}
