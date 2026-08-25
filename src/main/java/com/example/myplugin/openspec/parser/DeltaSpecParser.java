package com.example.myplugin.openspec.parser;

import com.example.myplugin.openspec.model.DeltaPlan;
import com.example.myplugin.openspec.model.RenamePair;
import com.example.myplugin.openspec.model.RequirementBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a delta-formatted spec file (inside openspec/changes/<name>/specs/)
 * into a DeltaPlan with ADDED/MODIFIED/REMOVED/RENAMED operations.
 */
public class DeltaSpecParser {

    private static final Pattern REQUIREMENT_HEADER =
        Pattern.compile("^###\\s*Requirement:\\s*(.+)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOP_LEVEL_HEADER = Pattern.compile("^##\\s+");
    private static final Pattern H3_HEADER = Pattern.compile("^###\\s+(.+?)\\s*$");
    private static final Pattern BULLET_REMOVED =
        Pattern.compile("^\\s*-\\s*`?###\\s*Requirement:\\s*(.+?)`?\\s*$");
    private static final Pattern FROM_PATTERN =
        Pattern.compile("^\\s*-?\\s*FROM:\\s*`?###\\s*Requirement:\\s*(.+?)`?\\s*$");
    private static final Pattern TO_PATTERN =
        Pattern.compile("^\\s*-?\\s*TO:\\s*`?###\\s*Requirement:\\s*(.+?)`?\\s*$");

    public DeltaPlan parse(String content) {
        if (content == null || content.isEmpty()) {
            return new DeltaPlan(new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                false, false, false, false);
        }

        String normalized = MarkdownSections.normalizeLineEndings(content);
        String[] lines = normalized.split("\n");
        boolean[] mask = CodeFenceMask.build(lines);

        Map<String, MarkdownSections.SectionBody> sections =
            MarkdownSections.splitTopLevelSections(content);

        // Find each delta section
        MarkdownSections.SectionMatch addedMatch =
            MarkdownSections.findSectionWithMeta(sections, "ADDED Requirements");
        MarkdownSections.SectionMatch modifiedMatch =
            MarkdownSections.findSectionWithMeta(sections, "MODIFIED Requirements");
        MarkdownSections.SectionMatch removedMatch =
            MarkdownSections.findSectionWithMeta(sections, "REMOVED Requirements");
        MarkdownSections.SectionMatch renamedMatch =
            MarkdownSections.findSectionWithMeta(sections, "RENAMED Requirements");

        List<String> skippedHeaders = new ArrayList<>();

        List<RequirementBlock> added = parseRequirementBlocksFromSection(
            addedMatch.getBody(), addedMatch.getTitle(), skippedHeaders);
        List<RequirementBlock> modified = parseRequirementBlocksFromSection(
            modifiedMatch.getBody(), modifiedMatch.getTitle(), skippedHeaders);
        List<String> removed = parseRemovedNames(removedMatch.getBody());
        List<RenamePair> renamed = parseRenamedPairs(renamedMatch.getBody());

        skippedHeaders.sort((a, b) -> a.compareTo(b));

        return new DeltaPlan(added, modified, removed, renamed, skippedHeaders,
            addedMatch.isFound(), modifiedMatch.isFound(),
            removedMatch.isFound(), renamedMatch.isFound());
    }

    private List<RequirementBlock> parseRequirementBlocksFromSection(
            MarkdownSections.SectionBody sectionBody,
            String sectionTitle,
            List<String> skippedHeaders) {
        if (sectionBody == null || sectionBody.isEmpty()) {
            return new ArrayList<>();
        }

        String[] lines = sectionBody.getLines();
        boolean[] mask = sectionBody.getFenceMask();
        List<RequirementBlock> blocks = new ArrayList<>();
        int i = 0;

        while (i < lines.length) {
            // Skip until requirement header
            while (i < lines.length && !isRequirementHeader(lines[i], mask)) {
                // Record skipped H3 headers
                recordIfSkippedHeader(lines, mask, i, sectionTitle, sectionBody.getBodyStartLine(), skippedHeaders);
                i++;
            }
            if (i >= lines.length) break;

            String headerLine = lines[i];
            Matcher m = REQUIREMENT_HEADER.matcher(headerLine);
            if (!m.find()) { i++; continue; }

            String name = m.group(1).trim();
            i++;

            // Collect body until next requirement header or top-level header
            StringBuilder raw = new StringBuilder();
            raw.append(headerLine);
            while (i < lines.length && !isRequirementHeader(lines[i], mask) && !isTopLevelHeader(lines[i], mask)) {
                recordIfSkippedHeader(lines, mask, i, sectionTitle, sectionBody.getBodyStartLine(), skippedHeaders);
                raw.append("\n").append(lines[i]);
                i++;
            }

            blocks.add(new RequirementBlock(headerLine, name, raw.toString().trim()));
        }

        return blocks;
    }

    private void recordIfSkippedHeader(String[] lines, boolean[] mask, int index,
                                        String sectionTitle, int bodyStartLine,
                                        List<String> skippedHeaders) {
        if (mask[index]) return;
        Matcher h3 = H3_HEADER.matcher(lines[index]);
        if (h3.find() && !REQUIREMENT_HEADER.matcher(lines[index]).find()) {
            skippedHeaders.add(sectionTitle + ":" + h3.group(1).trim() + " at line " + (bodyStartLine + index));
        }
    }

    private List<String> parseRemovedNames(MarkdownSections.SectionBody sectionBody) {
        if (sectionBody == null || sectionBody.isEmpty()) {
            return new ArrayList<>();
        }

        String[] lines = sectionBody.getLines();
        boolean[] mask = sectionBody.getFenceMask();
        List<String> names = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            if (mask[i]) continue;

            // Try ### Requirement: header
            Matcher m = REQUIREMENT_HEADER.matcher(lines[i]);
            if (m.find()) {
                names.add(m.group(1).trim());
                continue;
            }

            // Try bullet list format
            Matcher bullet = BULLET_REMOVED.matcher(lines[i]);
            if (bullet.find()) {
                names.add(bullet.group(1).trim());
            }
        }
        return names;
    }

    private List<RenamePair> parseRenamedPairs(MarkdownSections.SectionBody sectionBody) {
        if (sectionBody == null || sectionBody.isEmpty()) {
            return new ArrayList<>();
        }

        String[] lines = sectionBody.getLines();
        boolean[] mask = sectionBody.getFenceMask();
        List<RenamePair> pairs = new ArrayList<>();
        String currentFrom = null;

        for (int i = 0; i < lines.length; i++) {
            if (mask[i]) continue;

            Matcher fromMatch = FROM_PATTERN.matcher(lines[i]);
            if (fromMatch.find()) {
                currentFrom = fromMatch.group(1).trim();
                continue;
            }

            Matcher toMatch = TO_PATTERN.matcher(lines[i]);
            if (toMatch.find()) {
                String to = toMatch.group(1).trim();
                if (currentFrom != null) {
                    pairs.add(new RenamePair(currentFrom, to));
                    currentFrom = null;
                }
            }
        }
        return pairs;
    }

    private boolean isRequirementHeader(String line, boolean[] mask) {
        // Need index-based check but this is simplified
        return REQUIREMENT_HEADER.matcher(line).find();
    }

    private boolean isTopLevelHeader(String line, boolean[] mask) {
        return TOP_LEVEL_HEADER.matcher(line).find();
    }
}
