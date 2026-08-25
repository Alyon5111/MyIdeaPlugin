package com.example.myplugin.openspec;

import com.example.myplugin.openspec.model.DeltaPlan;
import com.example.myplugin.openspec.model.MergeResult;
import com.example.myplugin.openspec.merger.DeltaMerger;
import com.example.myplugin.openspec.parser.DeltaSpecParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeltaMergerTest {

    private DeltaMerger merger;
    private DeltaSpecParser deltaParser;

    private static final String MAIN_SPEC =
        "# User Service\n\n" +
        "## Purpose\n\n" +
        "Manage users.\n\n" +
        "## Requirements\n\n" +
        "### Requirement: Create User\n\n" +
        "The system SHALL create users.\n\n" +
        "#### Scenario: Valid input\n\n" +
        "- WHEN input is valid\n" +
        "- THEN user is created\n\n" +
        "### Requirement: Delete User\n\n" +
        "The system SHALL delete users.\n\n" +
        "#### Scenario: Existing user\n\n" +
        "- WHEN user exists\n" +
        "- THEN user is deleted\n\n" +
        "### Requirement: Rename User\n\n" +
        "The system SHALL rename users.\n";

    @BeforeEach
    void setUp() {
        merger = new DeltaMerger();
        deltaParser = new DeltaSpecParser();
    }

    @Test
    void mergeAddedRequirement() {
        String deltaContent =
            "## ADDED Requirements\n\n" +
            "### Requirement: Find User\n\n" +
            "The system SHALL find users by email.\n\n" +
            "#### Scenario: User exists\n\n" +
            "- WHEN user with email exists\n" +
            "- THEN return user\n";

        DeltaPlan delta = deltaParser.parse(deltaContent);
        MergeResult result = merger.merge(MAIN_SPEC, delta);

        assertFalse(result.isRetired());
        assertEquals(1, result.getCounts().getAdded());
        assertEquals(0, result.getCounts().getModified());
        assertEquals(0, result.getCounts().getRemoved());

        assertTrue(result.getRebuilt().contains("Find User"));
        assertTrue(result.getRebuilt().contains("Create User"));
        assertTrue(result.getRebuilt().contains("Delete User"));
    }

    @Test
    void mergeRemovedRequirement() {
        String deltaContent =
            "## REMOVED Requirements\n\n" +
            "### Requirement: Delete User\n";

        DeltaPlan delta = deltaParser.parse(deltaContent);
        MergeResult result = merger.merge(MAIN_SPEC, delta);

        assertEquals(1, result.getCounts().getRemoved());
        assertTrue(result.getRebuilt().contains("Create User"));
        assertFalse(result.getRebuilt().contains("Delete User"));
    }

    @Test
    void mergeModifiedRequirement() {
        String deltaContent =
            "## MODIFIED Requirements\n\n" +
            "### Requirement: Create User\n\n" +
            "The system SHALL create users with validation.\n\n" +
            "#### Scenario: Valid input\n\n" +
            "- WHEN input is valid\n" +
            "- THEN user is created\n\n" +
            "#### Scenario: Invalid input\n\n" +
            "- WHEN input is invalid\n" +
            "- THEN reject with error\n";

        DeltaPlan delta = deltaParser.parse(deltaContent);
        MergeResult result = merger.merge(MAIN_SPEC, delta);

        assertEquals(1, result.getCounts().getModified());
        assertTrue(result.getRebuilt().contains("validation"));
        assertTrue(result.getRebuilt().contains("Invalid input"));
    }

    @Test
    void mergeRenamedRequirement() {
        String deltaContent =
            "## RENAMED Requirements\n\n" +
            "- FROM: `### Requirement: Rename User`\n" +
            "- TO: `### Requirement: Update User Name`\n";

        DeltaPlan delta = deltaParser.parse(deltaContent);
        MergeResult result = merger.merge(MAIN_SPEC, delta);

        assertEquals(1, result.getCounts().getRenamed());
        assertTrue(result.getRebuilt().contains("Update User Name"));
        assertFalse(result.getRebuilt().contains("Rename User"));
    }

    @Test
    void mergeRetiresWhenAllRemoved() {
        // Create a spec with only one requirement
        String simpleSpec =
            "# Simple\n\n## Purpose\n\nSimple.\n\n## Requirements\n\n" +
            "### Requirement: Only One\n\nThe system SHALL one.\n";

        String deltaContent =
            "## REMOVED Requirements\n\n" +
            "### Requirement: Only One\n";

        DeltaPlan delta = deltaParser.parse(deltaContent);
        MergeResult result = merger.merge(simpleSpec, delta);

        assertTrue(result.isRetired());
    }

    @Test
    void mergePreservesStructure() {
        String deltaContent =
            "## ADDED Requirements\n\n" +
            "### Requirement: Z Last\n\n" +
            "The system SHALL z.\n";

        DeltaPlan delta = deltaParser.parse(deltaContent);
        MergeResult result = merger.merge(MAIN_SPEC, delta);

        // Purpose should be preserved
        assertTrue(result.getRebuilt().contains("Manage users."));
        // Title should be preserved
        assertTrue(result.getRebuilt().contains("# User Service"));
        // Original requirements should be in order
        int createUserIdx = result.getRebuilt().indexOf("Create User");
        int deleteUserIdx = result.getRebuilt().indexOf("Delete User");
        int renameUserIdx = result.getRebuilt().indexOf("Rename User");
        int zLastIdx = result.getRebuilt().indexOf("Z Last");

        assertTrue(createUserIdx < deleteUserIdx);
        assertTrue(deleteUserIdx < renameUserIdx);
        assertTrue(renameUserIdx < zLastIdx);
    }

    @Test
    void mergeHandlesNearMissTypo() {
        String deltaContent =
            "## REMOVED Requirements\n\n" +
            "### Requirement: delete user\n";  // lowercase: different case only

        DeltaPlan delta = deltaParser.parse(deltaContent);
        MergeResult result = merger.merge(MAIN_SPEC, delta);

        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().get(0).contains("did you mean"));
    }

    @Test
    void mergeHandlesDuplicateAdidtion() {
        String deltaContent =
            "## ADDED Requirements\n\n" +
            "### Requirement: Create User\n\n" +  // already exists
            "The system SHALL create users v2.\n";

        DeltaPlan delta = deltaParser.parse(deltaContent);
        MergeResult result = merger.merge(MAIN_SPEC, delta);

        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().get(0).contains("already exists"));
    }
}
