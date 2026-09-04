package com.example.myplugin.agent.review;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ResponseAnalyzer {

    private static final List<Pattern> PERFORMATIVE_PATTERNS = new ArrayList<>();

    static {
        PERFORMATIVE_PATTERNS.add(Pattern.compile("(?i)you'?re\\s+absolutely\\s+right"));
        PERFORMATIVE_PATTERNS.add(Pattern.compile("(?i)great\\s+point"));
        PERFORMATIVE_PATTERNS.add(Pattern.compile("(?i)excellent\\s+feedback"));
        PERFORMATIVE_PATTERNS.add(Pattern.compile("(?i)thanks\\s+for\\s+"));
        PERFORMATIVE_PATTERNS.add(Pattern.compile("(?i)good\\s+catch"));
        PERFORMATIVE_PATTERNS.add(Pattern.compile("(?i)wonderful\\s+suggestion"));
        PERFORMATIVE_PATTERNS.add(Pattern.compile("(?i)perfect\\s+"));
        PERFORMATIVE_PATTERNS.add(Pattern.compile("(?i)awesome\\s+"));
        PERFORMATIVE_PATTERNS.add(Pattern.compile("(?i)fantastic\\s+"));
    }

    public static List<String> detectPerformativeResponses(String input) {
        List<String> detected = new ArrayList<>();
        if (input == null) return detected;

        for (Pattern pattern : PERFORMATIVE_PATTERNS) {
            if (pattern.matcher(input).find()) {
                detected.add(pattern.pattern());
            }
        }
        return detected;
    }

    public static boolean hasPerformativeResponses(String input) {
        return !detectPerformativeResponses(input).isEmpty();
    }

    public static String getPerformativeMessage(List<String> responses) {
        if (responses.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("PERFORMATIVE RESPONSES DETECTED:\n");
        for (String response : responses) {
            sb.append("- ").append(response).append("\n");
        }
        sb.append("\nINSTEAD:\n");
        sb.append("- Restate the technical requirement\n");
        sb.append("- Ask clarifying questions\n");
        sb.append("- Push back with technical reasoning if wrong\n");
        sb.append("- Just start working (actions > words)\n");
        return sb.toString();
    }

    public static boolean isYagniCandidate(String feature) {
        if (feature == null) return false;

        String lower = feature.toLowerCase();
        return lower.contains("proper") || lower.contains("real") ||
            lower.contains("production") || lower.contains("enterprise") ||
            lower.contains("scalable") || lower.contains("extensible");
    }

    public static String getYagniMessage(String feature) {
        return "YAGNI CHECK: '" + feature + "'\n" +
            "Ask: Is this actually used? If not, remove it.\n" +
            "Grepping codebase for actual usage...";
    }
}
