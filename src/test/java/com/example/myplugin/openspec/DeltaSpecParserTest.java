package com.example.myplugin.openspec;

import com.example.myplugin.openspec.model.DeltaPlan;
import com.example.myplugin.openspec.model.RequirementBlock;
import com.example.myplugin.openspec.model.RenamePair;
import com.example.myplugin.openspec.parser.DeltaSpecParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeltaSpecParserTest {

    private final DeltaSpecParser parser = new DeltaSpecParser();

    @Test
    void parseAddedRequirements() {
        String content = "## ADDED Requirements\n\n" +
            "### Requirement: New Feature\n\n" +
            "The system SHALL do something new.\n\n" +
            "#### Scenario: Basic case\n\n" +
            "- WHEN user triggers\n" +
            "- THEN system responds\n";

        DeltaPlan delta = parser.parse(content);

        assertTrue(delta.hasAdded());
        assertEquals(1, delta.getAdded().size());
        assertEquals("New Feature", delta.getAdded().get(0).getName());
        assertFalse(delta.hasModified());
        assertFalse(delta.hasRemoved());
        assertFalse(delta.hasRenamed());
    }

    @Test
    void parseModifiedRequirements() {
        String content = "## MODIFIED Requirements\n\n" +
            "### Requirement: Existing Feature\n\n" +
            "Updated description with new behavior.\n\n" +
            "#### Scenario: Updated case\n\n" +
            "- WHEN something\n" +
            "- THEN result\n";

        DeltaPlan delta = parser.parse(content);

        assertTrue(delta.hasModified());
        assertEquals(1, delta.getModified().size());
        assertEquals("Existing Feature", delta.getModified().get(0).getName());
    }

    @Test
    void parseRemovedRequirements() {
        String content = "## REMOVED Requirements\n\n" +
            "### Requirement: Deprecated Feature\n\n" +
            "### Requirement: Old Feature\n";

        DeltaPlan delta = parser.parse(content);

        assertTrue(delta.hasRemoved());
        assertEquals(2, delta.getRemoved().size());
        assertEquals("Deprecated Feature", delta.getRemoved().get(0));
        assertEquals("Old Feature", delta.getRemoved().get(1));
    }

    @Test
    void parseRenamedRequirements() {
        String content = "## RENAMED Requirements\n\n" +
            "- FROM: `### Requirement: Old Name`\n" +
            "- TO: `### Requirement: New Name`\n";

        DeltaPlan delta = parser.parse(content);

        assertTrue(delta.hasRenamed());
        assertEquals(1, delta.getRenamed().size());
        assertEquals("Old Name", delta.getRenamed().get(0).getFrom());
        assertEquals("New Name", delta.getRenamed().get(0).getTo());
    }

    @Test
    void parseMixedDelta() {
        String content = "## ADDED Requirements\n\n" +
            "### Requirement: A\n\n" +
            "The system SHALL a.\n\n" +
            "## MODIFIED Requirements\n\n" +
            "### Requirement: B\n\n" +
            "Updated B.\n\n" +
            "## REMOVED Requirements\n\n" +
            "### Requirement: C\n";

        DeltaPlan delta = parser.parse(content);

        assertTrue(delta.hasAdded());
        assertTrue(delta.hasModified());
        assertTrue(delta.hasRemoved());
        assertEquals(1, delta.getAdded().size());
        assertEquals(1, delta.getModified().size());
        assertEquals(1, delta.getRemoved().size());
    }

    @Test
    void parseEmptyDelta() {
        DeltaPlan delta = parser.parse("");
        assertTrue(delta.isEmpty());
    }

    @Test
    void parseRemovedAsBulletList() {
        String content = "## REMOVED Requirements\n\n" +
            "- `### Requirement: Foo`\n" +
            "- `### Requirement: Bar`\n";

        DeltaPlan delta = parser.parse(content);

        assertEquals(2, delta.getRemoved().size());
        assertEquals("Foo", delta.getRemoved().get(0));
        assertEquals("Bar", delta.getRemoved().get(1));
    }
}
