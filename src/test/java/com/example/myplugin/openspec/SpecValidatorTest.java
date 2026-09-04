package com.example.myplugin.openspec;

import com.example.myplugin.openspec.validator.SpecValidator;
import com.example.myplugin.openspec.validator.SpecValidator.ValidationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpecValidatorTest {

    private final SpecValidator validator = new SpecValidator();

    @Test
    void validateValidSpec() {
        String content = "# User Authentication\n\n" +
            "## Purpose\n\n" +
            "Manage user login and session.\n\n" +
            "## Requirements\n\n" +
            "### Requirement: Login\n\n" +
            "The system SHALL allow users to login.\n\n" +
            "#### Scenario: Successful login\n\n" +
            "- WHEN user provides valid credentials\n" +
            "- THEN system creates a session\n";

        ValidationResult result = validator.validate(content);

        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void validateEmptySpec() {
        ValidationResult result = validator.validate("");

        assertFalse(result.isValid());
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().get(0).contains("empty"));
    }

    @Test
    void validateNullSpec() {
        ValidationResult result = validator.validate(null);

        assertFalse(result.isValid());
        assertFalse(result.getErrors().isEmpty());
    }

    @Test
    void validateMissingPurpose() {
        String content = "# Test\n\n## Requirements\n\n" +
            "### Requirement: Foo\n\n" +
            "The system SHALL foo.\n";

        ValidationResult result = validator.validate(content);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().get(0).contains("Purpose"));
    }

    @Test
    void validateMissingRequirements() {
        String content = "# Test\n\n## Purpose\n\nTest purpose here.\n";

        ValidationResult result = validator.validate(content);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().get(0).contains("requirement"));
    }

    @Test
    void validateShortPurpose() {
        String content = "# Test\n\n## Purpose\n\nShort.\n\n## Requirements\n\n" +
            "### Requirement: Foo\n\n" +
            "The system SHALL foo.\n";

        ValidationResult result = validator.validate(content);

        assertTrue(result.isValid());
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().get(0).contains("short"));
    }

    @Test
    void validateRequirementWithoutShall() {
        String content = "# Test\n\n## Purpose\n\nThis is a test purpose.\n\n## Requirements\n\n" +
            "### Requirement: Foo\n\n" +
            "The system does something.\n\n" +
            "#### Scenario: Basic\n\n" +
            "- WHEN triggered\n" +
            "- THEN happens\n";

        ValidationResult result = validator.validate(content);

        assertTrue(result.isValid());
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("SHALL")));
    }

    @Test
    void validateRequirementWithoutScenario() {
        String content = "# Test\n\n## Purpose\n\nThis is a test purpose.\n\n## Requirements\n\n" +
            "### Requirement: Foo\n\n" +
            "The system SHALL foo.\n";

        ValidationResult result = validator.validate(content);

        assertTrue(result.isValid());
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("no scenarios")));
    }
}
