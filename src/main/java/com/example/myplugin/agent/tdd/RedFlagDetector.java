package com.example.myplugin.agent.tdd;

import java.util.ArrayList;
import java.util.List;

public class RedFlagDetector {

    private final List<String> redFlags = new ArrayList<>();

    public List<String> getRedFlags() { return redFlags; }
    public void clear() { redFlags.clear(); }

    public void detectCodeBeforeTest(String recentActions) {
        String lower = recentActions.toLowerCase();
        boolean hasImplementation = lower.contains("write_file") || lower.contains("edit_file");
        boolean hasTest = lower.contains("run_tests") || lower.contains("test");

        if (hasImplementation && !hasTest) {
            redFlags.add("RED FLAG: Code written before running tests. TDD requires tests first.");
        }
    }

    public void detectTestImmediatelyPassing(String testOutput) {
        if (testOutput == null) return;
        String lower = testOutput.toLowerCase();

        if (lower.contains("build successful") && !lower.contains("failed")) {
            redFlags.add("RED FLAG: Test passes immediately. Did you write a failing test first?");
        }
    }

    public void detectRationalization(String userMessage) {
        String lower = userMessage.toLowerCase();

        String[] patterns = {
            "too simple", "don't need test", "skip test", "no test needed",
            "just this once", "will add tests later", "manual test",
            "tdd is too", "waste of time", "overkill",
            "i already tested", "it works", "should work"
        };

        for (String pattern : patterns) {
            if (lower.contains(pattern)) {
                redFlags.add("RATIONALIZATION DETECTED: '" + pattern + "' - This is a common excuse to skip TDD.");
            }
        }
    }

    public void detectTestAfterImplementation(String implTimestamp, String testTimestamp) {
        if (implTimestamp != null && testTimestamp != null) {
            try {
                long implTime = Long.parseLong(implTimestamp);
                long testTime = Long.parseLong(testTimestamp);

                if (implTime < testTime) {
                    long diffMinutes = (testTime - implTime) / 60000;
                    if (diffMinutes > 5) {
                        redFlags.add("RED FLAG: Test written " + diffMinutes + " minutes after implementation. " +
                            "Tests should be written FIRST.");
                    }
                }
            } catch (NumberFormatException e) {
                // Ignore parse errors
            }
        }
    }

    public void detectExcessiveImplementation(String testCount, String implLines) {
        try {
            int tests = Integer.parseInt(testCount);
            int lines = Integer.parseInt(implLines);

            if (tests == 0 && lines > 10) {
                redFlags.add("RED FLAG: " + lines + " lines of code with no tests. Every feature needs tests.");
            }

            if (tests > 0 && lines / tests > 50) {
                redFlags.add("WARNING: Implementation is " + (lines / tests) + " lines per test. " +
                    "Consider if this is minimal enough.");
            }
        } catch (NumberFormatException e) {
            // Ignore parse errors
        }
    }

    public String generateReport() {
        if (redFlags.isEmpty()) {
            return "No red flags detected. TDD workflow is being followed correctly.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== TDD Red Flags Detected ===\n\n");
        for (int i = 0; i < redFlags.size(); i++) {
            sb.append(i + 1).append(". ").append(redFlags.get(i)).append("\n");
        }
        sb.append("\n=== Recommendation ===\n");
        sb.append("Stop and address these issues before continuing.\n");
        sb.append("Remember: Tests FIRST, implementation SECOND.\n");
        return sb.toString();
    }

    public boolean hasRedFlags() { return !redFlags.isEmpty(); }
}
