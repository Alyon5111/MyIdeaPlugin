package com.example.myplugin.agent.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class PlanValidator {

    private static final String[] PLACEHOLDER_PATTERNS = {
        "TBD", "TODO", "FIXME", "placeholder", "fill in",
        "add appropriate", "implement later", "write tests for the above"
    };

    private static final Pattern SIMILAR_TO_PATTERN = Pattern.compile("(?i)similar to\\s+task\\s+\\d+");

    public ValidationResult validate(String planContent) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (planContent == null || planContent.trim().isEmpty()) {
            errors.add("Plan is empty");
            return new ValidationResult(false, errors, warnings);
        }

        scanPlaceholders(planContent, errors);
        scanSimilarTo(planContent, errors);
        checkTestFirstOrder(planContent, warnings);
        checkSpecCoverage(planContent, warnings);
        checkTypeConsistency(planContent, warnings);

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    private void scanPlaceholders(String content, List<String> errors) {
        String lower = content.toLowerCase();
        for (String pattern : PLACEHOLDER_PATTERNS) {
            if (lower.contains(pattern.toLowerCase())) {
                errors.add("PLACEHOLDER FOUND: '" + pattern + "' is not allowed in plans");
            }
        }
    }

    private void scanSimilarTo(String content, List<String> errors) {
        var matcher = SIMILAR_TO_PATTERN.matcher(content);
        while (matcher.find()) {
            errors.add("SIMILAR-TO FOUND: '" + matcher.group() + "' is not allowed. Repeat full code.");
        }
    }

    private void checkTestFirstOrder(String content, List<String> warnings) {
        String[] lines = content.split("\n");
        boolean foundImplement = false;
        boolean foundTestBeforeImplement = false;

        for (String line : lines) {
            String lower = line.toLowerCase();
            if (lower.contains("step") && lower.contains("write failing test")) {
                foundTestBeforeImplement = true;
            } else if (lower.contains("step") && (lower.contains("implement") || lower.contains("write code"))) {
                if (!foundTestBeforeImplement) {
                    warnings.add("TEST-FIRST VIOLATION: Implementation step found before test step");
                }
                foundTestBeforeImplement = false;
            }
        }
    }

    private void checkSpecCoverage(String content, List<String> warnings) {
        if (!content.contains("## Task ")) {
            warnings.add("NO TASKS: Plan has no task sections");
        }
    }

    private void checkTypeConsistency(String content, List<String> warnings) {
        // Simple check: look for common type name patterns
        // In a full implementation, this would parse Java type references
    }

    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;

        public ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = errors;
            this.warnings = warnings;
        }

        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
    }
}
