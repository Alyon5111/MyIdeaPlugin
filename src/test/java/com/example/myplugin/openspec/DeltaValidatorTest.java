package com.example.myplugin.openspec;

import com.example.myplugin.openspec.validator.DeltaValidator;
import com.example.myplugin.openspec.validator.DeltaValidator.ValidationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeltaValidatorTest {

    private final DeltaValidator validator = new DeltaValidator();

    @Test
    void validateValidDelta() {
        String content = "## ADDED Requirements\n\n" +
            "### Requirement: New Feature\n\n" +
            "The system SHALL do something new.\n\n" +
            "#### Scenario: Basic case\n\n" +
            "- WHEN user triggers\n" +
            "- THEN system responds\n";

        ValidationResult result = validator.validate(content);

        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void validateEmptyDelta() {
        String content = "# Just a title\n\nSome text without any delta sections.\n";

        ValidationResult result = validator.validate(content);

        assertTrue(result.isValid());
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("no operations")));
    }

    @Test
    void validateNullDelta() {
        ValidationResult result = validator.validate(null);

        assertFalse(result.isValid());
        assertFalse(result.getErrors().isEmpty());
    }

    @Test
    void validateAddedWithoutScenario() {
        String content = "## ADDED Requirements\n\n" +
            "### Requirement: Foo\n\n" +
            "The system SHALL foo.\n";

        ValidationResult result = validator.validate(content);

        assertTrue(result.isValid());
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("no scenarios")));
    }

    @Test
    void validateDuplicateInAdded() {
        String content = "## ADDED Requirements\n\n" +
            "### Requirement: Foo\n\n" +
            "The system SHALL foo v1.\n\n" +
            "### Requirement: Foo\n\n" +
            "The system SHALL foo v2.\n";

        ValidationResult result = validator.validate(content);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Duplicate")));
    }

    @Test
    void validateConflictModifiedAndRemoved() {
        String content = "## MODIFIED Requirements\n\n" +
            "### Requirement: Bar\n\n" +
            "Updated bar.\n\n" +
            "#### Scenario: Updated\n\n" +
            "- WHEN bar\n" +
            "- THEN updated\n\n" +
            "## REMOVED Requirements\n\n" +
            "### Requirement: Bar\n";

        ValidationResult result = validator.validate(content);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("both MODIFIED and REMOVED")));
    }

    @Test
    void validateConflictAddedAndModified() {
        String content = "## ADDED Requirements\n\n" +
            "### Requirement: Baz\n\n" +
            "The system SHALL baz.\n\n" +
            "#### Scenario: Basic\n\n" +
            "- WHEN baz\n" +
            "- THEN ok\n\n" +
            "## MODIFIED Requirements\n\n" +
            "### Requirement: Baz\n\n" +
            "Updated baz.\n\n" +
            "#### Scenario: Updated\n\n" +
            "- WHEN baz\n" +
            "- THEN updated\n";

        ValidationResult result = validator.validate(content);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("both ADDED and MODIFIED")));
    }

    @Test
    void validateAgainstMainSpec_RemovedNotFound() {
        String deltaContent = "## REMOVED Requirements\n\n" +
            "### Requirement: NonExistent\n";

        String mainSpec = "# Test\n\n## Purpose\n\nTest.\n\n## Requirements\n\n" +
            "### Requirement: Existing\n\n" +
            "The system SHALL exist.\n";

        ValidationResult result = validator.validateAgainstMainSpec(deltaContent, mainSpec);

        assertTrue(result.isValid());
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("not found in main spec")));
    }

    @Test
    void validateAgainstMainSpec_ModifiedNotFound() {
        String deltaContent = "## MODIFIED Requirements\n\n" +
            "### Requirement: Ghost\n\n" +
            "Updated ghost.\n\n" +
            "#### Scenario: Updated\n\n" +
            "- WHEN ghost\n" +
            "- THEN updated\n";

        String mainSpec = "# Test\n\n## Purpose\n\nTest.\n\n## Requirements\n\n" +
            "### Requirement: Existing\n\n" +
            "The system SHALL exist.\n";

        ValidationResult result = validator.validateAgainstMainSpec(deltaContent, mainSpec);

        assertTrue(result.isValid());
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("not found in main spec")));
    }

    @Test
    void validateAgainstMainSpec_ScenarioLoss() {
        String deltaContent = "## MODIFIED Requirements\n\n" +
            "### Requirement: Foo\n\n" +
            "Updated foo without all scenarios.\n\n" +
            "#### Scenario: Success\n\n" +
            "- WHEN foo\n" +
            "- THEN ok\n";

        String mainSpec = "# Test\n\n## Purpose\n\nTest.\n\n## Requirements\n\n" +
            "### Requirement: Foo\n\n" +
            "The system SHALL foo.\n\n" +
            "#### Scenario: Success\n\n" +
            "- WHEN foo\n" +
            "- THEN ok\n\n" +
            "#### Scenario: Failure\n\n" +
            "- WHEN invalid\n" +
            "- THEN error\n";

        ValidationResult result = validator.validateAgainstMainSpec(deltaContent, mainSpec);

        assertTrue(result.isValid());
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("lose scenario")));
    }
}
