package com.example.myplugin.agent.tdd;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestAnalyzer {

    private static final Pattern BUILD_SUCCESS = Pattern.compile("(?i)BUILD\\s+SUCCESSFUL");
    private static final Pattern BUILD_FAILED = Pattern.compile("(?i)BUILD\\s+FAILED");
    private static final Pattern TESTS_PASSED = Pattern.compile("(?i)(\\d+)\\s+tests?\\s+passed");
    private static final Pattern TESTS_FAILED = Pattern.compile("(?i)(\\d+)\\s+tests?\\s+failed");
    private static final Pattern TEST_FAILURE = Pattern.compile("(?i)test.*failed|assertion.*error|expected.*but.*was");
    private static final Pattern COMPILATION_ERROR = Pattern.compile("(?i)compilation\\s+error|cannot\\s+find\\s+symbol");
    private static final Pattern CLASS_NOT_FOUND = Pattern.compile("(?i)classnotfound|class\\s+not\\s+found");
    private static final Pattern NULL_POINTER = Pattern.compile("(?i)nullpointer|null\\s+pointer|null_pointer");
    private static final Pattern EXCEPTION = Pattern.compile("(?i)exception|error");

    public enum Outcome {
        TEST_FAILS_AS_EXPECTED,
        TEST_PASSES,
        TEST_ERROR,
        TEST_CRASH,
        UNKNOWN
    }

    public static Analysis analyze(String testOutput) {
        if (testOutput == null || testOutput.trim().isEmpty()) {
            return new Analysis(Outcome.UNKNOWN, "Empty output");
        }

        String lower = testOutput.toLowerCase();

        if (BUILD_SUCCESS.matcher(lower).find()) {
            if (TESTS_FAILED.matcher(lower).find() || TEST_FAILURE.matcher(lower).find()) {
                return new Analysis(Outcome.TEST_FAILS_AS_EXPECTED, "Tests failed (expected)");
            }
            Matcher passedMatcher = TESTS_PASSED.matcher(lower);
            if (passedMatcher.find()) {
                int count = Integer.parseInt(passedMatcher.group(1));
                if (count > 0) {
                    return new Analysis(Outcome.TEST_PASSES, count + " tests passed");
                }
            }
            return new Analysis(Outcome.TEST_PASSES, "Build successful, no failures reported");
        }

        if (BUILD_FAILED.matcher(lower).find()) {
            if (NULL_POINTER.matcher(lower).find()) {
                return new Analysis(Outcome.TEST_CRASH, "NullPointerException in test");
            }

            if (CLASS_NOT_FOUND.matcher(lower).find()) {
                return new Analysis(Outcome.TEST_FAILS_AS_EXPECTED, "Class not found (expected for missing implementation)");
            }

            if (COMPILATION_ERROR.matcher(lower).find()) {
                return new Analysis(Outcome.TEST_ERROR, extractCompilationError(testOutput));
            }

            if (EXCEPTION.matcher(lower).find()) {
                return new Analysis(Outcome.TEST_CRASH, extractException(testOutput));
            }

            if (TEST_FAILURE.matcher(lower).find()) {
                return new Analysis(Outcome.TEST_FAILS_AS_EXPECTED, extractFailureReason(testOutput));
            }

            return new Analysis(Outcome.TEST_FAILS_AS_EXPECTED, "Build failed");
        }

        if (TEST_FAILURE.matcher(lower).find()) {
            return new Analysis(Outcome.TEST_FAILS_AS_EXPECTED, extractFailureReason(testOutput));
        }

        if (lower.contains("exception") || lower.contains("error")) {
            return new Analysis(Outcome.TEST_CRASH, extractException(testOutput));
        }

        return new Analysis(Outcome.UNKNOWN, "Could not determine test outcome from output");
    }

    private static String extractFailureReason(String output) {
        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.toLowerCase().contains("expected") && line.toLowerCase().contains("but")) {
                return line.trim();
            }
            if (line.toLowerCase().contains("assertion") && line.toLowerCase().contains("error")) {
                return line.trim();
            }
        }
        for (String line : lines) {
            if (line.toLowerCase().contains("test") && line.toLowerCase().contains("failed")) {
                return line.trim();
            }
        }
        return "Test failed";
    }

    private static String extractCompilationError(String output) {
        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.toLowerCase().contains("compilation error") || line.toLowerCase().contains("cannot find symbol")) {
                return line.trim();
            }
        }
        return "Compilation error";
    }

    private static String extractException(String output) {
        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.toLowerCase().contains("exception")) {
                return line.trim();
            }
        }
        return "Exception occurred";
    }

    public static class Analysis {
        private final Outcome outcome;
        private final String reason;

        public Analysis(Outcome outcome, String reason) {
            this.outcome = outcome;
            this.reason = reason;
        }

        public Outcome getOutcome() { return outcome; }
        public String getReason() { return reason; }
        public boolean isExpectedFailure() { return outcome == Outcome.TEST_FAILS_AS_EXPECTED; }
        public boolean isPass() { return outcome == Outcome.TEST_PASSES; }
        public boolean isError() { return outcome == Outcome.TEST_ERROR || outcome == Outcome.TEST_CRASH; }
    }
}
