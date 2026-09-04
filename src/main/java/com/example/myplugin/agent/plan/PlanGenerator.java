package com.example.myplugin.agent.plan;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class PlanGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public String generateFromDesignDoc(String designDocContent, String projectPath) {
        StringBuilder sb = new StringBuilder();

        String title = extractTitle(designDocContent);
        String overview = extractSection(designDocContent, "Overview");

        sb.append("# ").append(title).append(" Implementation Plan\n\n");
        sb.append("> **For agentic workers:** Use TDD workflow for each step.\n\n");
        sb.append("**Goal:** ").append(overview.isEmpty() ? "Implement " + title : overview).append("\n\n");
        sb.append("**Architecture:** See design document.\n\n");
        sb.append("**Tech Stack:** Java 21, IntelliJ Platform SDK, LangChain4j\n\n");

        sb.append("## File Structure\n\n");
        sb.append("| File | Action | Purpose |\n");
        sb.append("|------|--------|---------|\n");
        sb.append("| See design doc | CREATE/MODIFY | As specified in design |\n\n");

        List<String> sections = extractSections(designDocContent);
        int taskNum = 1;
        for (String section : sections) {
            if (!section.equals("Overview") && !section.equals("Alternatives Considered")) {
                sb.append(generateTask(taskNum, section, designDocContent));
                taskNum++;
            }
        }

        if (taskNum == 1) {
            sb.append(generateGenericTask(1, title, designDocContent));
        }

        return sb.toString();
    }

    public String generateFromDescription(String description, String projectPath) {
        StringBuilder sb = new StringBuilder();

        String title = description.length() > 50 ? description.substring(0, 50) : description;

        sb.append("# ").append(title).append(" Implementation Plan\n\n");
        sb.append("> **For agentic workers:** Use TDD workflow for each step.\n\n");
        sb.append("**Goal:** ").append(description).append("\n\n");
        sb.append("**Architecture:** To be determined during implementation.\n\n");
        sb.append("**Tech Stack:** Java 21, IntelliJ Platform SDK, LangChain4j\n\n");

        sb.append("## File Structure\n\n");
        sb.append("| File | Action | Purpose |\n");
        sb.append("|------|--------|---------|\n");
        sb.append("| TBD | CREATE | As needed |\n\n");

        sb.append(generateGenericTask(1, title, description));

        return sb.toString();
    }

    private String extractTitle(String content) {
        for (String line : content.split("\n")) {
            if (line.startsWith("# ")) {
                return line.substring(2).replace(" Design", "").trim();
            }
        }
        return "Feature";
    }

    private String extractSection(String content, String sectionName) {
        String[] lines = content.split("\n");
        boolean inSection = false;
        StringBuilder sb = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("## " + sectionName)) {
                inSection = true;
                continue;
            }
            if (inSection) {
                if (line.startsWith("## ")) break;
                sb.append(line).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private List<String> extractSections(String content) {
        List<String> sections = new ArrayList<>();
        for (String line : content.split("\n")) {
            if (line.startsWith("## ")) {
                sections.add(line.substring(3).trim());
            }
        }
        return sections;
    }

    private String generateTask(int num, String sectionName, String designDoc) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Task ").append(num).append(": ").append(sectionName).append("\n\n");
        sb.append("**Files:** See design document\n\n");

        sb.append("- [ ] Step 1: Write failing test for ").append(sectionName.toLowerCase().replace(" ", "_")).append("\n");
        sb.append("  Run: `./gradlew test`\n");
        sb.append("  Expected: BUILD FAILED (test not found)\n\n");
        sb.append("```java\n// Test code here\n```\n\n");

        sb.append("- [ ] Step 2: Implement ").append(sectionName).append("\n");
        sb.append("  Run: `./gradlew test`\n");
        sb.append("  Expected: BUILD SUCCESSFUL\n\n");
        sb.append("```java\n// Implementation code here\n```\n\n");

        sb.append("- [ ] Step 3: Commit\n");
        sb.append("  Run: `git add -A && git commit -m \"feat: add ").append(sectionName.toLowerCase().replace(" ", "-")).append("\"`\n\n");

        return sb.toString();
    }

    private String generateGenericTask(int num, String title, String description) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Task ").append(num).append(": ").append(title).append("\n\n");
        sb.append("**Files:** TBD\n\n");

        sb.append("- [ ] Step 1: Write failing test\n");
        sb.append("  Run: `./gradlew test`\n");
        sb.append("  Expected: BUILD FAILED\n\n");
        sb.append("```java\n// Test code\n```\n\n");

        sb.append("- [ ] Step 2: Implement feature\n");
        sb.append("  Run: `./gradlew test`\n");
        sb.append("  Expected: BUILD SUCCESSFUL\n\n");
        sb.append("```java\n// Implementation\n```\n\n");

        sb.append("- [ ] Step 3: Commit\n");
        sb.append("  Run: `git add -A && git commit -m \"feat: ").append(title.toLowerCase().replace(" ", "-")).append("\"`\n\n");

        return sb.toString();
    }

    public String getPlanFileName(String featureName) {
        String slug = featureName.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return LocalDate.now().format(DATE_FMT) + "-" + slug + "-plan.md";
    }
}
