package com.example.myplugin.agent.plan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlanValidatorTest {

    private final PlanValidator validator = new PlanValidator();

    @Test
    void validateValidPlan() {
        String plan = "# Feature Plan\n\n## Task 1: Core\n\n- [ ] Step 1: Write failing test\n- [ ] Step 2: Implement\n";

        PlanValidator.ValidationResult result = validator.validate(plan);

        assertTrue(result.isValid());
    }

    @Test
    void validateEmptyPlan() {
        PlanValidator.ValidationResult result = validator.validate("");

        assertFalse(result.isValid());
        assertTrue(result.getErrors().get(0).contains("empty"));
    }

    @Test
    void validateNullPlan() {
        PlanValidator.ValidationResult result = validator.validate(null);

        assertFalse(result.isValid());
    }

    @Test
    void detectPlaceholderTBD() {
        String plan = "# Plan\n\n## Task 1\n\n- [ ] Step 1: TBD\n";

        PlanValidator.ValidationResult result = validator.validate(plan);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("TBD")));
    }

    @Test
    void detectPlaceholderTODO() {
        String plan = "# Plan\n\n## Task 1\n\n- [ ] Step 1: TODO implement\n";

        PlanValidator.ValidationResult result = validator.validate(plan);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("TODO")));
    }

    @Test
    void detectSimilarTo() {
        String plan = "# Plan\n\n## Task 1\n\n- [ ] Step 1: Similar to Task 2\n";

        PlanValidator.ValidationResult result = validator.validate(plan);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("SIMILAR-TO")));
    }

    @Test
    void detectAddAppropriate() {
        String plan = "# Plan\n\n## Task 1\n\n- [ ] Step 1: Add appropriate error handling\n";

        PlanValidator.ValidationResult result = validator.validate(plan);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("add appropriate")));
    }

    @Test
    void noTasksWarning() {
        String plan = "# Plan\n\nSome content without tasks\n";

        PlanValidator.ValidationResult result = validator.validate(plan);

        assertTrue(result.isValid());
        assertFalse(result.getWarnings().isEmpty());
    }
}
