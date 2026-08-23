package com.example.myplugin.service;

import com.example.myplugin.model.Conversation;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ConversationStorageService {

    private static final String STORAGE_DIR = ".idea";
    private static final String STORAGE_FILE = "myplugin-conversations.json";

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .setPrettyPrinting()
            .create();

    private static final Type LIST_TYPE = new TypeToken<List<Conversation>>() {}.getType();

    public static void save(@NotNull Project project, @NotNull List<Conversation> conversations) {
        Path file = getStoragePath(project);
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(conversations, writer);
            }
        } catch (IOException e) {
            System.err.println("[MyPlugin] Failed to save conversations: " + e.getMessage());
        }
    }

    public static List<Conversation> load(@NotNull Project project) {
        Path file = getStoragePath(project);
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<Conversation> result = GSON.fromJson(reader, LIST_TYPE);
            return result != null ? result : new ArrayList<>();
        } catch (Exception e) {
            System.err.println("[MyPlugin] Failed to load conversations: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public static void deleteAll(@NotNull Project project) {
        Path file = getStoragePath(project);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            System.err.println("[MyPlugin] Failed to delete conversations: " + e.getMessage());
        }
    }

    @NotNull
    private static Path getStoragePath(@NotNull Project project) {
        return Path.of(project.getBasePath(), STORAGE_DIR, STORAGE_FILE);
    }
}
