package com.example.myplugin.openspec;

import com.example.myplugin.openspec.model.Spec;
import com.example.myplugin.openspec.model.RequirementBlock;
import com.example.myplugin.openspec.parser.SpecParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpecParserTest {

    private final SpecParser parser = new SpecParser();

    @Test
    void parseBasicSpec() {
        String content = "# User Authentication\n\n" +
            "## Purpose\n\n" +
            "Manage user login and session.\n\n" +
            "## Requirements\n\n" +
            "### Requirement: Login\n\n" +
            "The system SHALL allow users to login with email and password.\n\n" +
            "#### Scenario: Successful login\n\n" +
            "- WHEN user provides valid credentials\n" +
            "- THEN system creates a session\n\n" +
            "#### Scenario: Invalid password\n\n" +
            "- WHEN user provides wrong password\n" +
            "- THEN system rejects the login\n";

        Spec spec = parser.parse(content);

        assertEquals("User Authentication", spec.getTitle());
        assertEquals("Manage user login and session.", spec.getPurpose());
        assertEquals(1, spec.getRequirements().size());

        RequirementBlock login = spec.getRequirements().get(0);
        assertEquals("Login", login.getName());
        assertTrue(login.getRaw().contains("SHALL"));
    }

    @Test
    void parseMultipleRequirements() {
        String content = "# Order Service\n\n" +
            "## Purpose\n\n" +
            "Handle order processing.\n\n" +
            "## Requirements\n\n" +
            "### Requirement: Create Order\n\n" +
            "The system SHALL create orders.\n\n" +
            "#### Scenario: Basic order\n\n" +
            "- WHEN user submits order\n" +
            "- THEN order is created\n\n" +
            "### Requirement: Cancel Order\n\n" +
            "The system SHALL allow order cancellation.\n\n" +
            "#### Scenario: Cancel pending order\n\n" +
            "- WHEN user cancels pending order\n" +
            "- THEN order is cancelled\n";

        Spec spec = parser.parse(content);

        assertEquals(2, spec.getRequirements().size());
        assertEquals("Create Order", spec.getRequirements().get(0).getName());
        assertEquals("Cancel Order", spec.getRequirements().get(1).getName());
    }

    @Test
    void parseEmptySpec() {
        Spec spec = parser.parse("");
        assertEquals("", spec.getTitle());
        assertEquals("", spec.getPurpose());
        assertTrue(spec.getRequirements().isEmpty());
    }

    @Test
    void parseNullSpec() {
        Spec spec = parser.parse(null);
        assertTrue(spec.getRequirements().isEmpty());
    }

    @Test
    void findRequirement() {
        String content = "# Test\n\n## Purpose\n\nTest.\n\n## Requirements\n\n" +
            "### Requirement: Foo\n\nThe system SHALL foo.\n\n" +
            "### Requirement: Bar\n\nThe system SHALL bar.\n";

        Spec spec = parser.parse(content);

        assertNotNull(spec.findRequirement("Foo"));
        assertNotNull(spec.findRequirement("Bar"));
        assertNull(spec.findRequirement("Baz"));
        assertTrue(spec.hasRequirement("Foo"));
        assertFalse(spec.hasRequirement("Baz"));
    }

    @Test
    void parseScenarioNames() {
        String reqRaw = "### Requirement: Login\n\n" +
            "The system SHALL login.\n\n" +
            "#### Scenario: Success\n\n" +
            "- WHEN valid\n" +
            "- THEN ok\n\n" +
            "#### Scenario: Failure\n\n" +
            "- WHEN invalid\n" +
            "- THEN error\n";

        List<String> scenarios = SpecParser.parseScenarioNames(reqRaw);
        assertEquals(2, scenarios.size());
        assertEquals("Success", scenarios.get(0));
        assertEquals("Failure", scenarios.get(1));
    }

    @Test
    void parseSpecWithCodeFence() {
        String content = "# Test\n\n## Purpose\n\nTest.\n\n## Requirements\n\n" +
            "### Requirement: Foo\n\n" +
            "```\n" +
            "### This is NOT a requirement header\n" +
            "```\n\n" +
            "The system SHALL foo.\n";

        Spec spec = parser.parse(content);
        assertEquals(1, spec.getRequirements().size());
        assertEquals("Foo", spec.getRequirements().get(0).getName());
    }
}
