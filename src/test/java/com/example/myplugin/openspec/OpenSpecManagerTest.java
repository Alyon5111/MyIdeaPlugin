package com.example.myplugin.openspec;

import com.example.myplugin.openspec.manager.OpenSpecManager;
import com.example.myplugin.openspec.manager.OpenSpecManager.ChangeInfo;
import com.example.myplugin.openspec.manager.OpenSpecManager.SpecInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpenSpecManagerTest {

    @TempDir
    Path tempDir;

    private OpenSpecManager manager;

    @BeforeEach
    void setUp() {
        manager = new OpenSpecManager(tempDir);
    }

    @Test
    void init_CreatesDirectoryStructure() throws IOException {
        manager.init();

        assertTrue(Files.exists(manager.getOpenspecDir()));
        assertTrue(Files.exists(manager.getSpecsDir()));
        assertTrue(Files.exists(manager.getChangesDir()));
        assertTrue(Files.exists(manager.getArchiveDir()));
    }

    @Test
    void isInitialized_ChecksExistence() {
        assertFalse(manager.isInitialized());

        try {
            manager.init();
        } catch (IOException e) {
            fail("Init failed");
        }

        assertTrue(manager.isInitialized());
    }

    @Test
    void listSpecs_ReturnsAllSpecs() throws IOException {
        manager.init();

        Path specDir = manager.getSpecsDir().resolve("auth");
        Files.createDirectories(specDir);
        String specContent = "# Auth\n\n## Purpose\n\nAuthentication.\n\n## Requirements\n\n" +
            "### Requirement: Login\n\n" +
            "The system SHALL login.\n\n" +
            "#### Scenario: Success\n\n" +
            "- WHEN valid\n" +
            "- THEN ok\n";
        Files.writeString(specDir.resolve("spec.md"), specContent, StandardCharsets.UTF_8);

        List<SpecInfo> specs = manager.listSpecs();

        assertEquals(1, specs.size());
        assertEquals("auth", specs.get(0).getName());
        assertEquals("Auth", specs.get(0).getTitle());
        assertEquals(1, specs.get(0).getRequirementCount());
    }

    @Test
    void listChanges_ReturnsAllChanges() throws IOException {
        manager.init();

        manager.createChange("add-feature");
        manager.createChange("fix-bug");

        List<ChangeInfo> changes = manager.listChanges();

        assertEquals(2, changes.size());
        assertTrue(changes.stream().anyMatch(c -> c.getName().equals("add-feature")));
        assertTrue(changes.stream().anyMatch(c -> c.getName().equals("fix-bug")));
    }

    @Test
    void createChange_ScaffoldStructure() throws IOException {
        manager.init();

        Path changeDir = manager.createChange("my-change");

        assertTrue(Files.exists(changeDir));
        assertTrue(Files.exists(changeDir.resolve("proposal.md")));
        assertTrue(Files.exists(changeDir.resolve("tasks.md")));
        assertTrue(Files.exists(changeDir.resolve("specs")));

        String proposal = Files.readString(changeDir.resolve("proposal.md"), StandardCharsets.UTF_8);
        assertTrue(proposal.contains("my-change"));

        String tasks = Files.readString(changeDir.resolve("tasks.md"), StandardCharsets.UTF_8);
        assertTrue(tasks.contains("- [ ]"));
    }

    @Test
    void archiveChange_MergesAndMoves() throws IOException {
        manager.init();

        Path changeDir = manager.createChange("test-change");
        Path deltaSpecDir = changeDir.resolve("specs").resolve("auth");
        Files.createDirectories(deltaSpecDir);

        String deltaContent = "## ADDED Requirements\n\n" +
            "### Requirement: Register\n\n" +
            "The system SHALL register users.\n\n" +
            "#### Scenario: Valid email\n\n" +
            "- WHEN email valid\n" +
            "- THEN registered\n";
        Files.writeString(deltaSpecDir.resolve("spec.md"), deltaContent, StandardCharsets.UTF_8);

        OpenSpecManager.ArchiveResult result = manager.archiveChange("test-change");

        assertNotNull(result.getArchiveName());
        assertTrue(result.getUpdatedSpecs().size() > 0);

        assertFalse(Files.exists(manager.getChangesDir().resolve("test-change")));
        assertTrue(Files.exists(manager.getArchiveDir().resolve(result.getArchiveName())));

        Path mergedSpec = manager.getSpecsDir().resolve("auth").resolve("spec.md");
        assertTrue(Files.exists(mergedSpec));
        String merged = Files.readString(mergedSpec, StandardCharsets.UTF_8);
        assertTrue(merged.contains("Register"));
    }

    @Test
    void archiveChange_NonexistentChange_ThrowsException() {
        assertThrows(IOException.class, () -> manager.archiveChange("nonexistent"));
    }

    @Test
    void archiveChange_WithRetiredSpec() throws IOException {
        manager.init();

        Path specDir = manager.getSpecsDir().resolve("old");
        Files.createDirectories(specDir);
        String specContent = "# Old\n\n## Purpose\n\nOld spec.\n\n## Requirements\n\n" +
            "### Requirement: Only\n\n" +
            "The system SHALL only.\n";
        Files.writeString(specDir.resolve("spec.md"), specContent, StandardCharsets.UTF_8);

        Path changeDir = manager.createChange("remove-old");
        Path deltaSpecDir = changeDir.resolve("specs").resolve("old");
        Files.createDirectories(deltaSpecDir);
        String deltaContent = "## REMOVED Requirements\n\n### Requirement: Only\n";
        Files.writeString(deltaSpecDir.resolve("spec.md"), deltaContent, StandardCharsets.UTF_8);

        OpenSpecManager.ArchiveResult result = manager.archiveChange("remove-old");

        assertFalse(Files.exists(manager.getSpecsDir().resolve("old").resolve("spec.md")));
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("Retired")));
    }

    @Test
    void archiveChange_WithWarnings() throws IOException {
        manager.init();

        Path changeDir = manager.createChange("typo-change");
        Path deltaSpecDir = changeDir.resolve("specs").resolve("auth");
        Files.createDirectories(deltaSpecDir);

        Path mainSpecDir = manager.getSpecsDir().resolve("auth");
        Files.createDirectories(mainSpecDir);
        String mainContent = "# Auth\n\n## Purpose\n\nAuth system.\n\n## Requirements\n\n" +
            "### Requirement: Login\n\n" +
            "The system SHALL login.\n";
        Files.writeString(mainSpecDir.resolve("spec.md"), mainContent, StandardCharsets.UTF_8);

        String deltaContent = "## REMOVED Requirements\n\n### Requirement: login\n";
        Files.writeString(deltaSpecDir.resolve("spec.md"), deltaContent, StandardCharsets.UTF_8);

        OpenSpecManager.ArchiveResult result = manager.archiveChange("typo-change");

        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("did you mean")));
    }
}
