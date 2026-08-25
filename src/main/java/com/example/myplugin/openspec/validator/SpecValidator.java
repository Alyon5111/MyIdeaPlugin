package com.example.myplugin.openspec.validator;

import com.example.myplugin.openspec.model.RequirementBlock;
import com.example.myplugin.openspec.model.Spec;
import com.example.myplugin.openspec.parser.SpecParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates main spec files against OpenSpec conventions.
 */
public class SpecValidator {

    private static final int MIN_PURPOSE_LENGTH = 10;
    private static final int MAX_PURPOSE_LENGTH = 500;
    private static final String SHALL_KEYWORD = "(?i)\\b(SHALL|MUST|SHOULD|MAY)\\b";

    public ValidationResult validate(String content) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (content == null || content.trim().isEmpty()) {
            errors.add("Spec file is empty");
            return new ValidationResult(false, errors, warnings);
        }

        SpecParser parser = new SpecParser();
        Spec spec = parser.parse(content);

        // Check title
        if (spec.getTitle().isEmpty()) {
            warnings.add("Spec has no title (# header)");
        }

        // Check purpose
        if (spec.getPurpose().isEmpty()) {
            errors.add("Spec must have a ## Purpose section");
        } else if (spec.getPurpose().length() < MIN_PURPOSE_LENGTH) {
            warnings.add("Purpose is very short (" + spec.getPurpose().length() + " chars, recommended " + MIN_PURPOSE_LENGTH + "+)");
        }

        // Check requirements
        if (spec.getRequirements().isEmpty()) {
            errors.add("Spec must have at least one requirement");
        }

        for (RequirementBlock req : spec.getRequirements()) {
            // Check requirement has SHALL/MUST keyword
            if (!req.getRaw().matches(".*" + SHALL_KEYWORD + ".*")) {
                warnings.add("Requirement '" + req.getName() + "' should use SHALL/MUST/SHOULD/MAY keyword");
            }

            // Check requirement has at least one scenario
            List<String> scenarios = SpecParser.parseScenarioNames(req.getRaw());
            if (scenarios.isEmpty()) {
                warnings.add("Requirement '" + req.getName() + "' has no scenarios");
            }
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
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
