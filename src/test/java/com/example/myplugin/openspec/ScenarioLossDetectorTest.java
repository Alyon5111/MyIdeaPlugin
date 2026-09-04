package com.example.myplugin.openspec;

import com.example.myplugin.openspec.merger.ScenarioLossDetector;
import com.example.myplugin.openspec.model.RequirementBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioLossDetectorTest {

    private RequirementBlock makeBlock(String name, String raw) {
        return new RequirementBlock("### Requirement: " + name, name, raw);
    }

    @Test
    void findMissingScenarios_NoneMissing() {
        RequirementBlock current = makeBlock("Foo",
            "### Requirement: Foo\n\n" +
            "The system SHALL foo.\n\n" +
            "#### Scenario: Success\n\n" +
            "- WHEN foo\n" +
            "- THEN ok\n\n" +
            "#### Scenario: Failure\n\n" +
            "- WHEN bad\n" +
            "- THEN error\n");

        RequirementBlock incoming = makeBlock("Foo",
            "### Requirement: Foo\n\n" +
            "Updated foo.\n\n" +
            "#### Scenario: Success\n\n" +
            "- WHEN foo\n" +
            "- THEN ok\n\n" +
            "#### Scenario: Failure\n\n" +
            "- WHEN bad\n" +
            "- THEN error\n");

        List<String> missing = ScenarioLossDetector.findMissingScenarios(current, incoming);

        assertTrue(missing.isEmpty());
    }

    @Test
    void findMissingScenarios_SingleMissing() {
        RequirementBlock current = makeBlock("Foo",
            "### Requirement: Foo\n\n" +
            "The system SHALL foo.\n\n" +
            "#### Scenario: Success\n\n" +
            "- WHEN foo\n" +
            "- THEN ok\n\n" +
            "#### Scenario: Failure\n\n" +
            "- WHEN bad\n" +
            "- THEN error\n");

        RequirementBlock incoming = makeBlock("Foo",
            "### Requirement: Foo\n\n" +
            "Updated foo.\n\n" +
            "#### Scenario: Success\n\n" +
            "- WHEN foo\n" +
            "- THEN ok\n");

        List<String> missing = ScenarioLossDetector.findMissingScenarios(current, incoming);

        assertEquals(1, missing.size());
        assertEquals("Failure", missing.get(0));
    }

    @Test
    void findMissingScenarios_MultipleMissing() {
        RequirementBlock current = makeBlock("Foo",
            "### Requirement: Foo\n\n" +
            "The system SHALL foo.\n\n" +
            "#### Scenario: A\n\n" +
            "- WHEN a\n" +
            "- THEN a\n\n" +
            "#### Scenario: B\n\n" +
            "- WHEN b\n" +
            "- THEN b\n\n" +
            "#### Scenario: C\n\n" +
            "- WHEN c\n" +
            "- THEN c\n");

        RequirementBlock incoming = makeBlock("Foo",
            "### Requirement: Foo\n\n" +
            "Updated foo.\n\n" +
            "#### Scenario: A\n\n" +
            "- WHEN a\n" +
            "- THEN a\n");

        List<String> missing = ScenarioLossDetector.findMissingScenarios(current, incoming);

        assertEquals(2, missing.size());
        assertTrue(missing.contains("B"));
        assertTrue(missing.contains("C"));
    }

    @Test
    void findMissingScenarios_MultiplicityAware() {
        RequirementBlock current = makeBlock("Foo",
            "### Requirement: Foo\n\n" +
            "The system SHALL foo.\n\n" +
            "#### Scenario: Same\n\n" +
            "- WHEN x\n" +
            "- THEN y\n\n" +
            "#### Scenario: Same\n\n" +
            "- WHEN x\n" +
            "- THEN y\n\n" +
            "#### Scenario: Same\n\n" +
            "- WHEN x\n" +
            "- THEN y\n");

        RequirementBlock incoming = makeBlock("Foo",
            "### Requirement: Foo\n\n" +
            "Updated foo.\n\n" +
            "#### Scenario: Same\n\n" +
            "- WHEN x\n" +
            "- THEN y\n");

        List<String> missing = ScenarioLossDetector.findMissingScenarios(current, incoming);

        assertEquals(2, missing.size());
        assertEquals("Same", missing.get(0));
        assertEquals("Same", missing.get(1));
    }

    @Test
    void parseScenarioCounts_ViaMultiplicityAwareness() {
        RequirementBlock current = makeBlock("Foo",
            "### Requirement: Foo\n\n" +
            "The system SHALL foo.\n\n" +
            "#### Scenario: A\n\n" +
            "- WHEN a\n" +
            "- THEN a\n\n" +
            "#### Scenario: B\n\n" +
            "- WHEN b\n" +
            "- THEN b\n\n" +
            "#### Scenario: A\n\n" +
            "- WHEN a2\n" +
            "- THEN a2\n");

        RequirementBlock incoming = makeBlock("Foo",
            "### Requirement: Foo\n\n" +
            "Updated.\n\n" +
            "#### Scenario: A\n\n" +
            "- WHEN a\n" +
            "- THEN a\n");

        List<String> missing = ScenarioLossDetector.findMissingScenarios(current, incoming);

        assertEquals(2, missing.size());
        assertTrue(missing.contains("B"));
        assertTrue(missing.contains("A"));
    }
}
