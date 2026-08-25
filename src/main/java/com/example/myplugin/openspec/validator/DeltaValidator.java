package com.example.myplugin.openspec.validator;

import com.example.myplugin.openspec.model.DeltaPlan;
import com.example.myplugin.openspec.model.RequirementBlock;
import com.example.myplugin.openspec.model.Spec;
import com.example.myplugin.openspec.parser.DeltaSpecParser;
import com.example.myplugin.openspec.parser.SpecParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates delta spec files against OpenSpec conventions.
 */
public class DeltaValidator {

    private final DeltaSpecParser deltaSpecParser = new DeltaSpecParser();
    private final SpecParser specParser = new SpecParser();

    public ValidationResult validate(String deltaContent) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (deltaContent == null || deltaContent.trim().isEmpty()) {
            errors.add("Delta spec file is empty");
            return new ValidationResult(false, errors, warnings);
        }

        DeltaPlan delta = deltaSpecParser.parse(deltaContent);

        if (delta.isEmpty()) {
            warnings.add("Delta spec has no operations (no ADDED/MODIFIED/REMOVED/RENAMED sections)");
            return new ValidationResult(true, errors, warnings);
        }

        // Validate ADDED requirements have at least one scenario
        for (RequirementBlock block : delta.getAdded()) {
            List<String> scenarios = SpecParser.parseScenarioNames(block.getRaw());
            if (scenarios.isEmpty()) {
                warnings.add("ADDED requirement '" + block.getName() + "' has no scenarios");
            }
        }

        // Validate MODIFIED requirements have at least one scenario
        for (RequirementBlock block : delta.getModified()) {
            List<String> scenarios = SpecParser.parseScenarioNames(block.getRaw());
            if (scenarios.isEmpty()) {
                warnings.add("MODIFIED requirement '" + block.getName() + "' has no scenarios");
            }
        }

        // Check for duplicates within sections
        checkDuplicates(delta.getAdded(), "ADDED", errors);
        checkDuplicates(delta.getModified(), "MODIFIED", errors);

        // Check for cross-section conflicts (MODIFIED + REMOVED for same name)
        Set<String> removedNames = new HashSet<>(delta.getRemoved());
        for (RequirementBlock block : delta.getModified()) {
            if (removedNames.contains(block.getName())) {
                errors.add("Requirement '" + block.getName() + "' is both MODIFIED and REMOVED");
            }
        }

        // Check for ADDED + MODIFIED conflicts
        Set<String> modifiedNames = new HashSet<>();
        for (RequirementBlock block : delta.getModified()) {
            modifiedNames.add(block.getName());
        }
        for (RequirementBlock block : delta.getAdded()) {
            if (modifiedNames.contains(block.getName())) {
                errors.add("Requirement '" + block.getName() + "' is both ADDED and MODIFIED");
            }
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    private void checkDuplicates(List<RequirementBlock> blocks, String section, List<String> errors) {
        Set<String> seen = new HashSet<>();
        for (RequirementBlock block : blocks) {
            if (!seen.add(block.getName())) {
                errors.add("Duplicate requirement '" + block.getName() + "' in " + section + " section");
            }
        }
    }

    public ValidationResult validateAgainstMainSpec(String deltaContent, String mainSpecContent) {
        ValidationResult baseResult = validate(deltaContent);

        if (!baseResult.isValid()) return baseResult;

        List<String> errors = new ArrayList<>(baseResult.getErrors());
        List<String> warnings = new ArrayList<>(baseResult.getWarnings());

        DeltaPlan delta = deltaSpecParser.parse(deltaContent);
        Spec mainSpec = specParser.parse(mainSpecContent);

        // Check REMOVED requirements exist in main spec
        for (String name : delta.getRemoved()) {
            if (!mainSpec.hasRequirement(name)) {
                warnings.add("REMOVED requirement '" + name + "' not found in main spec");
            }
        }

        // Check MODIFIED requirements exist in main spec
        for (RequirementBlock block : delta.getModified()) {
            if (!mainSpec.hasRequirement(block.getName())) {
                warnings.add("MODIFIED requirement '" + block.getName() + "' not found in main spec");
            }
        }

        // Check for scenario loss in MODIFIED requirements
        for (RequirementBlock block : delta.getModified()) {
            RequirementBlock current = mainSpec.findRequirement(block.getName());
            if (current != null) {
                List<String> currentScenarios = SpecParser.parseScenarioNames(current.getRaw());
                List<String> incomingScenarios = SpecParser.parseScenarioNames(block.getRaw());
                Set<String> incomingSet = new HashSet<>(incomingScenarios);
                for (String scenario : currentScenarios) {
                    if (!incomingSet.contains(scenario)) {
                        warnings.add("MODIFIED requirement '" + block.getName() + "' would lose scenario: " + scenario);
                    }
                }
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
