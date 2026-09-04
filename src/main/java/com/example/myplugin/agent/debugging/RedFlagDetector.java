package com.example.myplugin.agent.debugging;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class RedFlagDetector {

    private static final List<Pattern> RED_FLAGS = new ArrayList<>();

    static {
        RED_FLAGS.add(Pattern.compile("(?i)quick\\s+fix"));
        RED_FLAGS.add(Pattern.compile("(?i)just\\s+try"));
        RED_FLAGS.add(Pattern.compile("(?i)see\\s+if\\s+it\\s+works"));
        RED_FLAGS.add(Pattern.compile("(?i)probably\\s+\\w+"));
        RED_FLAGS.add(Pattern.compile("(?i)might\\s+work"));
        RED_FLAGS.add(Pattern.compile("(?i)let\\s+me\\s+fix"));
        RED_FLAGS.add(Pattern.compile("(?i)fix\\s+\\d+"));
        RED_FLAGS.add(Pattern.compile("(?i)add\\s+multiple\\s+changes"));
        RED_FLAGS.add(Pattern.compile("(?i)skip\\s+the\\s+test"));
        RED_FLAGS.add(Pattern.compile("(?i)manually\\s+verify"));
        RED_FLAGS.add(Pattern.compile("(?i)don'?t\\s+understand\\s+but"));
        RED_FLAGS.add(Pattern.compile("(?i)adapt\\s+\\w+\\s+differently"));
        RED_FLAGS.add(Pattern.compile("(?i)one\\s+more\\s+fix"));
        RED_FLAGS.add(Pattern.compile("(?i)investigate\\s+later"));
    }

    public static List<String> detectRedFlags(String input) {
        List<String> detected = new ArrayList<>();
        if (input == null) return detected;

        for (Pattern pattern : RED_FLAGS) {
            if (pattern.matcher(input).find()) {
                detected.add(pattern.pattern());
            }
        }
        return detected;
    }

    public static boolean hasRedFlags(String input) {
        return !detectRedFlags(input).isEmpty();
    }

    public static String getRedFlagMessage(List<String> flags) {
        if (flags.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("RED FLAGS DETECTED:\n");
        for (String flag : flags) {
            sb.append("- ").append(flag).append("\n");
        }
        sb.append("\nSTOP. Return to Phase 1 (Root Cause Investigation).");
        return sb.toString();
    }
}
