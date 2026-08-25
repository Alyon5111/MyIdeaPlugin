package com.example.myplugin.openspec.merger;

import com.example.myplugin.openspec.model.DeltaPlan;
import com.example.myplugin.openspec.model.MergeResult;
import com.example.myplugin.openspec.model.MergeResult.Counts;
import com.example.myplugin.openspec.model.RenamePair;
import com.example.myplugin.openspec.model.RequirementBlock;
import com.example.myplugin.openspec.parser.CodeFenceMask;
import com.example.myplugin.openspec.parser.MarkdownSections;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Merges delta spec operations into a main spec.
 * Operations are applied in order: RENAMED, REMOVED, MODIFIED, ADDED.
 */
public class DeltaMerger {

    private static final Pattern REQUIREMENT_HEADER =
        Pattern.compile("^###\\s*Requirement:\\s*(.+)\\s*$", Pattern.CASE_INSENSITIVE);

    /**
     * Merge a delta plan into a main spec.
     */
    public MergeResult merge(String mainSpecContent, DeltaPlan delta) {
        List<String> warnings = new ArrayList<>();

        ParsedMainSpec main = parseMainSpec(mainSpecContent);

        LinkedHashMap<String, String> reqMap = new LinkedHashMap<>();
        for (RequirementBlock block : main.requirements) {
            reqMap.put(block.getName(), block.getRaw());
        }

        int addedCount = 0, modifiedCount = 0, removedCount = 0, renamedCount = 0;

        // 1. Apply RENAMED
        for (RenamePair rename : delta.getRenamed()) {
            String oldName = rename.getFrom();
            String newName = rename.getTo();
            if (reqMap.containsKey(oldName)) {
                String raw = reqMap.remove(oldName);
                raw = raw.replaceFirst("(?i)###\\s*Requirement:\\s*" + Pattern.quote(oldName),
                    "### Requirement: " + newName);
                reqMap.put(newName, raw);
                renamedCount++;
            } else {
                warnings.add("RENAMED: requirement '" + oldName + "' not found in main spec");
            }
        }

        // 2. Apply REMOVED
        for (String name : delta.getRemoved()) {
            if (reqMap.containsKey(name)) {
                reqMap.remove(name);
                removedCount++;
            } else {
                String nearMiss = findNearMiss(name, reqMap.keySet());
                if (nearMiss != null) {
                    warnings.add("REMOVED: requirement '" + name + "' not found (did you mean '" + nearMiss + "'?)");
                } else {
                    warnings.add("REMOVED: requirement '" + name + "' not found in main spec");
                }
            }
        }

        // 3. Apply MODIFIED
        for (RequirementBlock modBlock : delta.getModified()) {
            String name = modBlock.getName();
            if (reqMap.containsKey(name)) {
                RequirementBlock current = findRequirementBlock(main.requirements, name);
                if (current != null) {
                    List<String> missing = ScenarioLossDetector.findMissingScenarios(current, modBlock);
                    if (!missing.isEmpty()) {
                        warnings.add("MODIFIED: requirement '" + name + "' would lose scenarios: " + missing);
                    }
                }
                reqMap.put(name, modBlock.getRaw());
                modifiedCount++;
            } else {
                warnings.add("MODIFIED: requirement '" + name + "' not found in main spec, treating as ADDED");
                reqMap.put(name, modBlock.getRaw());
                addedCount++;
            }
        }

        // 4. Apply ADDED
        for (RequirementBlock addBlock : delta.getAdded()) {
            String name = addBlock.getName();
            if (reqMap.containsKey(name)) {
                warnings.add("ADDED: requirement '" + name + "' already exists, skipping");
            } else {
                reqMap.put(name, addBlock.getRaw());
                addedCount++;
            }
        }

        // 5. Rebuild markdown
        String rebuilt = rebuildMarkdown(main, reqMap);

        // 6. Check for retirement
        boolean retired = reqMap.isEmpty() && !delta.getRemoved().isEmpty();

        // 7. Audit unaccounted content
        List<String> unaccounted = auditUnaccountedContent(rebuilt);

        Counts counts = new Counts(addedCount, modifiedCount, removedCount, renamedCount);
        return new MergeResult(rebuilt, retired, warnings, unaccounted, counts);
    }

    private String rebuildMarkdown(ParsedMainSpec main, LinkedHashMap<String, String> reqMap) {
        StringBuilder sb = new StringBuilder();

        sb.append(main.before);
        sb.append(main.reqHeaderLine).append("\n");

        if (!main.preamble.isEmpty()) {
            sb.append(main.preamble).append("\n\n");
        }

        boolean first = true;
        for (Map.Entry<String, String> entry : reqMap.entrySet()) {
            if (!first) sb.append("\n\n");
            sb.append(entry.getValue());
            first = false;
        }

        if (!main.after.isEmpty()) {
            if (!reqMap.isEmpty()) sb.append("\n\n");
            sb.append(main.after);
        }

        return sb.toString();
    }

    private List<String> auditUnaccountedContent(String rebuilt) {
        List<String> unaccounted = new ArrayList<>();
        String normalized = MarkdownSections.normalizeLineEndings(rebuilt);
        String[] lines = normalized.split("\n");
        boolean[] mask = CodeFenceMask.build(lines);

        boolean inRequirements = false;
        boolean inRequirementBlock = false;

        for (int i = 0; i < lines.length; i++) {
            if (mask[i]) continue;
            String trimmed = lines[i].trim();

            if (trimmed.isEmpty()) continue;

            if (trimmed.startsWith("# ") && !trimmed.startsWith("## ")) continue;
            if (trimmed.equals("## Purpose")) { inRequirements = false; inRequirementBlock = false; continue; }
            if (trimmed.matches("(?i)##\\s+Requirements")) { inRequirements = true; inRequirementBlock = false; continue; }
            if (trimmed.matches("^##\\s+") && !trimmed.matches("(?i)##\\s+(Purpose|Requirements)")) {
                inRequirements = false;
                inRequirementBlock = false;
                continue;
            }

            if (inRequirements) {
                if (trimmed.matches("(?i)^###\\s+Requirement:\\s+.+")) {
                    inRequirementBlock = true;
                    continue;
                }
                if (trimmed.matches("^####\\s+") && inRequirementBlock) continue;
                if (inRequirementBlock) {
                    if (trimmed.startsWith("- ") || trimmed.startsWith("  ") || trimmed.startsWith("* ")) continue;
                    if (trimmed.startsWith("**WHEN**") || trimmed.startsWith("**THEN**") || trimmed.startsWith("**AND**")) continue;
                    if (trimmed.startsWith("```")) continue;
                    if (!trimmed.startsWith("#")) continue;
                }
                unaccounted.add("Line " + (i + 1) + ": " + truncate(trimmed, 200));
            }
        }
        return unaccounted;
    }

    private String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }

    private String findNearMiss(String target, Set<String> candidates) {
        String folded = foldName(target);
        for (String candidate : candidates) {
            if (foldName(candidate).equals(folded)) return candidate;
        }
        return null;
    }

    private String foldName(String name) {
        return name.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    private RequirementBlock findRequirementBlock(List<RequirementBlock> blocks, String name) {
        for (RequirementBlock block : blocks) {
            if (block.getName().equals(name)) return block;
        }
        return null;
    }

    private ParsedMainSpec parseMainSpec(String content) {
        String normalized = MarkdownSections.normalizeLineEndings(content);
        String[] lines = normalized.split("\n");

        int reqHeaderIndex = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].matches("(?i)^##\\s+Requirements\\s*$")) {
                reqHeaderIndex = i;
                break;
            }
        }

        if (reqHeaderIndex == -1) {
            return new ParsedMainSpec(
                content.trim() + "\n\n",
                "## Requirements",
                "",
                new ArrayList<>(),
                "\n"
            );
        }

        int endIndex = lines.length;
        for (int i = reqHeaderIndex + 1; i < lines.length; i++) {
            if (lines[i].matches("^##\\s+")) {
                endIndex = i;
                break;
            }
        }

        String before = joinLines(lines, 0, reqHeaderIndex);
        String reqHeaderLine = lines[reqHeaderIndex];

        String[] sectionLines = subArray(lines, reqHeaderIndex + 1, endIndex);
        List<RequirementBlock> requirements = parseRequirementBlocksFromLines(sectionLines);

        String preamble = "";
        int firstReqIdx = -1;
        for (int i = 0; i < sectionLines.length; i++) {
            if (REQUIREMENT_HEADER.matcher(sectionLines[i]).find()) {
                firstReqIdx = i;
                break;
            }
        }
        if (firstReqIdx > 0) {
            preamble = joinLines(sectionLines, 0, firstReqIdx).trim();
        }

        String after = endIndex < lines.length ? joinLines(lines, endIndex, lines.length) : "\n";

        return new ParsedMainSpec(
            before.isEmpty() ? "" : before + "\n",
            reqHeaderLine,
            preamble,
            requirements,
            after.startsWith("\n") ? after : "\n" + after
        );
    }

    private String[] subArray(String[] arr, int from, int to) {
        return Arrays.copyOfRange(arr, from, to);
    }

    private List<RequirementBlock> parseRequirementBlocksFromLines(String[] lines) {
        List<RequirementBlock> blocks = new ArrayList<>();
        int i = 0;

        while (i < lines.length) {
            while (i < lines.length && !REQUIREMENT_HEADER.matcher(lines[i]).find()) {
                i++;
            }
            if (i >= lines.length) break;

            String headerLine = lines[i];
            Matcher m = REQUIREMENT_HEADER.matcher(headerLine);
            if (!m.find()) { i++; continue; }

            String name = m.group(1).trim();
            i++;

            StringBuilder raw = new StringBuilder(headerLine);
            while (i < lines.length && !REQUIREMENT_HEADER.matcher(lines[i]).find()
                   && !lines[i].matches("^##\\s+")) {
                raw.append("\n").append(lines[i]);
                i++;
            }

            blocks.add(new RequirementBlock(headerLine, name, raw.toString().trim()));
        }
        return blocks;
    }

    private String joinLines(String[] lines, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (i > from) sb.append("\n");
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    private static class ParsedMainSpec {
        final String before;
        final String reqHeaderLine;
        final String preamble;
        final List<RequirementBlock> requirements;
        final String after;

        ParsedMainSpec(String before, String reqHeaderLine, String preamble,
                       List<RequirementBlock> requirements, String after) {
            this.before = before;
            this.reqHeaderLine = reqHeaderLine;
            this.preamble = preamble;
            this.requirements = requirements;
            this.after = after;
        }
    }
}
