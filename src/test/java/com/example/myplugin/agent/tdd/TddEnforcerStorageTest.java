package com.example.myplugin.agent.tdd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TddEnforcerStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void saveThenLoadRoundTripsSnapshotFields() throws Exception {
        Path file = tempDir.resolve(".idea").resolve("myplugin-tdd-state.json");

        TddStateSnapshot snapshot = new TddStateSnapshot();
        snapshot.setState("GREEN_VERIFY");
        snapshot.setCycleCount(1);
        snapshot.setCurrentTask("task with 中文 characters");
        snapshot.setLinkedPipelineId("pipeline-1");
        snapshot.setHistory(List.of("RED: Writing failing test", "GREEN: Writing minimal implementation"));
        snapshot.setLastTestOutput("BUILD FAILED\n1 test failed");
        snapshot.setLastTestFailureReason("expected failure");

        TddEnforcerStorage.save(file, snapshot);
        assertTrue(Files.exists(file), "snapshot file should be written");

        TddStateSnapshot loaded = TddEnforcerStorage.load(file);
        assertNotNull(loaded);
        assertEquals("GREEN_VERIFY", loaded.getState());
        assertEquals(1, loaded.getCycleCount());
        assertEquals("task with 中文 characters", loaded.getCurrentTask());
        assertEquals("pipeline-1", loaded.getLinkedPipelineId());
        assertEquals(2, loaded.getHistory().size());
        assertEquals("RED: Writing failing test", loaded.getHistory().get(0));
        assertEquals("BUILD FAILED\n1 test failed", loaded.getLastTestOutput());
        assertEquals("expected failure", loaded.getLastTestFailureReason());
        assertNotNull(loaded.getSavedAt());
    }

    @Test
    void loadReturnsNullWhenFileMissing() {
        assertNull(TddEnforcerStorage.load(tempDir.resolve("nope.json")));
    }

    @Test
    void loadReturnsNullWhenFileNotJson() throws Exception {
        Path file = tempDir.resolve("bad.json");
        Files.writeString(file, "not json {");
        assertNull(TddEnforcerStorage.load(file));
    }

    @Test
    void saveWithNullFileIsNoOp() {
        TddStateSnapshot snapshot = new TddStateSnapshot();
        snapshot.setState("GREEN");
        TddEnforcerStorage.save(null, snapshot);
        assertNull(snapshot.getSavedAt(), "save(null) must not throw and stays inert");
    }
}