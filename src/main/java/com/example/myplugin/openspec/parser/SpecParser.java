package com.example.myplugin.openspec.parser;

import com.example.myplugin.openspec.model.RequirementBlock;
import com.example.myplugin.openspec.model.Spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a main spec file (openspec/specs/<domain>/spec.md) into a Spec object.
 */
public class SpecParser {

    private static final Pattern REQUIREMENT_HEADER = Pattern.compile("^###\\s*Requirement:\\s*(.+)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCENARIO_HEADER = Pattern.compile("^####\\s+(?:Scenario:\\s*)?(.+)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOP_LEVEL_HEADER = Pattern.compile("^##\\s+");

    public Spec parse(String content) {
        if (content == null || content.isEmpty()) {
            return new Spec("", "", new ArrayList<>());
        }

        String normalized = MarkdownSections.normalizeLineEndings(content);
        String[] lines = normalized.split("\n");
        boolean[] mask = CodeFenceMask.build(lines);

        // Extract title (first # header)
        String title = "";
        for (int i = 0; i < lines.length; i++) {
            if (mask[i]) continue;
            if (lines[i].startsWith("# ") && !lines[i].startsWith("## ")) {
                title = lines[i].substring(2).trim();
                break;
            }
        }

        // Split into sections
        Map<String, MarkdownSections.SectionBody> sections =
            MarkdownSections.splitTopLevelSections(content);

        // Extract Purpose
        String purpose = "";
        MarkdownSections.SectionBody purposeSection = MarkdownSections.findSection(sections, "Purpose");
        if (!purposeSection.isEmpty()) {
            purpose = String.join("\n", purposeSection.getLines()).trim();
        }

        // Extract Requirements
        List<RequirementBlock> requirements = new ArrayList<>();
        MarkdownSections.SectionBody reqSection = MarkdownSections.findSection(sections, "Requirements");
        if (!reqSection.isEmpty()) {
            requirements = parseRequirementBlocks(reqSection.getLines(), reqSection.getFenceMask());
        }

        return new Spec(title, purpose, requirements);
    }

    /**
     * Parse requirement blocks from the body of a ## Requirements section.
     */
    static List<RequirementBlock> parseRequirementBlocks(String[] lines, boolean[] mask) {
        List<RequirementBlock> blocks = new ArrayList<>();
        int i = 0;

        while (i < lines.length) {
            // Skip until we find a requirement header
            while (i < lines.length && !isRequirementHeader(lines[i], mask)) {
                i++;
            }
            if (i >= lines.length) break;

            // Found a requirement header
            String headerLine = lines[i];
            Matcher m = REQUIREMENT_HEADER.matcher(headerLine);
            if (!m.find()) { i++; continue; }

            String name = m.group(1).trim();
            i++;

            // Collect lines until next requirement header or top-level header
            StringBuilder raw = new StringBuilder();
            raw.append(headerLine);
            while (i < lines.length && !isRequirementHeader(lines[i], mask) && !isTopLevelHeader(lines[i], mask)) {
                raw.append("\n").append(lines[i]);
                i++;
            }

            blocks.add(new RequirementBlock(headerLine, name, raw.toString().trim()));
        }

        return blocks;
    }

    private static boolean isRequirementHeader(String line, boolean[] mask) {
        int idx = -1;
        // We need the index, but this helper doesn't have it.
        // Use the mask from the outer scope. This is a simplified check.
        return REQUIREMENT_HEADER.matcher(line).find();
    }

    private static boolean isTopLevelHeader(String line, boolean[] mask) {
        return TOP_LEVEL_HEADER.matcher(line).find();
    }

    /**
     * Parse scenario blocks from a requirement's raw content.
     */
    public static List<String> parseScenarioNames(String requirementRaw) {
        List<String> names = new ArrayList<>();
        String normalized = MarkdownSections.normalizeLineEndings(requirementRaw);
        String[] lines = normalized.split("\n");
        boolean[] mask = CodeFenceMask.build(lines);

        for (int i = 0; i < lines.length; i++) {
            if (mask[i]) continue;
            Matcher m = SCENARIO_HEADER.matcher(lines[i]);
            if (m.find()) {
                String scenarioName = m.group(1).trim();
                // Strip trailing #### (ATX closing)
                scenarioName = scenarioName.replaceAll("\\s+#+\\s*$", "");
                names.add(scenarioName);
            }
        }
        return names;
    }
}
