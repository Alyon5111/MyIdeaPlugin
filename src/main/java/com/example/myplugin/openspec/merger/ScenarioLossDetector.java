package com.example.myplugin.openspec.merger;

import com.example.myplugin.openspec.model.RequirementBlock;
import com.example.myplugin.openspec.parser.CodeFenceMask;
import com.example.myplugin.openspec.parser.MarkdownSections;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects scenario loss when a MODIFIED requirement block replaces the current one.
 * If the current block has scenarios that the incoming block doesn't carry,
 * those scenarios would be silently dropped.
 */
public class ScenarioLossDetector {

    private static final Pattern SCENARIO_HEADER =
        Pattern.compile("^####\\s+(?:Scenario:\\s*)?(.+)\\s*$", Pattern.CASE_INSENSITIVE);

    private ScenarioLossDetector() {}

    /**
     * Find scenario names present in current but missing in incoming.
     * Multiplicity-aware: if current has 3 scenarios named "A" and incoming has 1,
     * reports 2 missing.
     */
    public static List<String> findMissingScenarios(RequirementBlock current, RequirementBlock incoming) {
        Map<String, Integer> incomingCounts = parseScenarioCounts(incoming.getRaw());
        List<String> missing = new ArrayList<>();

        for (String scenarioName : parseScenarioNames(current.getRaw())) {
            int remaining = incomingCounts.getOrDefault(scenarioName, 0);
            if (remaining > 0) {
                incomingCounts.put(scenarioName, remaining - 1);
            } else {
                missing.add(scenarioName);
            }
        }
        return missing;
    }

    /**
     * Parse scenario names from requirement raw content, with multiplicity.
     */
    static Map<String, Integer> parseScenarioCounts(String raw) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String name : parseScenarioNames(raw)) {
            counts.merge(name, 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Parse all scenario names from requirement raw content.
     */
    static List<String> parseScenarioNames(String raw) {
        List<String> names = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return names;

        String normalized = MarkdownSections.normalizeLineEndings(raw);
        String[] lines = normalized.split("\n");
        boolean[] mask = CodeFenceMask.build(lines);

        for (int i = 0; i < lines.length; i++) {
            if (mask[i]) continue;
            Matcher m = SCENARIO_HEADER.matcher(lines[i]);
            if (m.find()) {
                String name = m.group(1).trim();
                // Strip ATX closing sequence (trailing # run)
                name = name.replaceAll("[ \\t]+#+[ \\t]*$", "");
                names.add(name);
            }
        }
        return names;
    }
}
