package com.example.myplugin.agent.tdd;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.project.Project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists the TDD enforcer's cycle state to {@code .idea/myplugin-tdd-state.json} so a
 * verified half-finished cycle survives an IDE restart. Load failures are treated as
 * "no persisted state" so a corrupt or missing file never blocks execution.
 */
public final class TddEnforcerStorage {

    private static final String FILE_NAME = "myplugin-tdd-state.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private TddEnforcerStorage() {
    }

    public static Path defaultPath(Project project) {
        if (project == null || project.getBasePath() == null) {
            return null;
        }
        return Path.of(project.getBasePath(), ".idea", FILE_NAME);
    }

    public static void save(Path file, TddStateSnapshot snapshot) {
        if (file == null || snapshot == null) {
            return;
        }
        snapshot.setSavedAt(java.time.LocalDateTime.now().toString());
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, GSON.toJson(snapshot), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Persistence is best-effort: a failed snapshot must not break the tool call.
        }
    }

    public static TddStateSnapshot load(Path file) {
        if (file == null || !Files.exists(file)) {
            return null;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            return GSON.fromJson(json, TddStateSnapshot.class);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }
}