package com.example.myplugin.agent.memory;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AgentMemoryService {

    @FunctionalInterface
    public interface Persistence {
        List<MemoryEntry> load();
        default void save(List<MemoryEntry> entries) {}
    }

    private final Persistence persistence;
    private final List<MemoryEntry> entries;
    private final Object lock = new Object();

    public AgentMemoryService(@NotNull Project project) {
        this(new Persistence() {
            @Override
            public List<MemoryEntry> load() {
                return MemoryStorageService.load(project);
            }

            @Override
            public void save(List<MemoryEntry> entries) {
                MemoryStorageService.save(project, entries);
            }
        });
    }

    public AgentMemoryService(Persistence persistence) {
        this.persistence = persistence;
        this.entries = new ArrayList<>(persistence.load());
    }

    public static AgentMemoryService inMemory() {
        List<MemoryEntry> store = new ArrayList<>();
        return new AgentMemoryService(new Persistence() {
            @Override
            public List<MemoryEntry> load() {
                return new ArrayList<>(store);
            }

            @Override
            public void save(List<MemoryEntry> entries) {
                store.clear();
                store.addAll(entries);
            }
        });
    }

    public MemoryEntry addEntry(MemoryCategory category, String content, String source, double confidence) {
        return addEntry(category, content, source, confidence, null);
    }

    public MemoryEntry addEntry(MemoryCategory category, String content, String source, double confidence, List<String> tags) {
        synchronized (lock) {
            // Deduplicate by content (case-insensitive)
            for (MemoryEntry existing : entries) {
                if (existing.getContent().equalsIgnoreCase(content.trim())) {
                    existing.recordAccess();
                    return existing;
                }
            }

            MemoryEntry entry = new MemoryEntry(category, content, source, confidence);
            if (tags != null) {
                for (String tag : tags) {
                    entry.addTag(tag);
                }
            }
            entries.add(entry);
            persist();
            return entry;
        }
    }

    public boolean removeEntry(String id) {
        synchronized (lock) {
            boolean removed = entries.removeIf(e -> e.getId().equals(id));
            if (removed) persist();
            return removed;
        }
    }

    @Nullable
    public MemoryEntry getEntry(String id) {
        synchronized (lock) {
            for (MemoryEntry entry : entries) {
                if (entry.getId().equals(id)) {
                    entry.recordAccess();
                    return entry;
                }
            }
            return null;
        }
    }

    public List<MemoryEntry> search(String query) {
        synchronized (lock) {
            String lower = query == null ? "" : query.toLowerCase();
            return entries.stream()
                    .filter(e -> e.getContent().toLowerCase().contains(lower) ||
                            e.getTags().stream().anyMatch(t -> t.toLowerCase().contains(lower)) ||
                            e.getCategory().name().toLowerCase().contains(lower))
                    .map(e -> { e.recordAccess(); return e; })
                    .collect(Collectors.toList());
        }
    }

    public List<MemoryEntry> getByCategory(MemoryCategory category) {
        synchronized (lock) {
            return entries.stream()
                    .filter(e -> e.getCategory() == category)
                    .collect(Collectors.toList());
        }
    }

    public List<MemoryEntry> getTopImportant(int limit) {
        synchronized (lock) {
            long now = System.currentTimeMillis();
            return entries.stream()
                    .sorted(Comparator.comparingDouble((MemoryEntry e) -> e.getImportanceScore(now)).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
        }
    }

    public List<MemoryEntry> getHighConfidence(int limit) {
        synchronized (lock) {
            return entries.stream()
                    .filter(e -> e.getConfidence() >= 0.7)
                    .sorted(Comparator.comparingDouble(MemoryEntry::getConfidence).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
        }
    }

    public List<MemoryEntry> getAllEntries() {
        synchronized (lock) {
            return new ArrayList<>(entries);
        }
    }

    public int size() {
        synchronized (lock) {
            return entries.size();
        }
    }

    public double getAvgConfidence() {
        synchronized (lock) {
            if (entries.isEmpty()) return 0;
            double sum = 0;
            for (MemoryEntry e : entries) sum += e.getConfidence();
            return sum / entries.size();
        }
    }

    public void prune(int maxEntries) {
        synchronized (lock) {
            if (entries.size() <= maxEntries) return;
            long now = System.currentTimeMillis();
            entries.sort(Comparator.comparingDouble((MemoryEntry e) -> e.getImportanceScore(now)).reversed());
            int overflow = entries.size() - maxEntries;
            for (int i = 0; i < overflow; i++) {
                entries.remove(entries.size() - 1);
            }
            persist();
        }
    }

    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== MEMORY SUMMARY ===\n");
        sb.append("Total entries: ").append(entries.size()).append("\n");
        sb.append("Average confidence: ").append(String.format("%.2f", getAvgConfidence())).append("\n\n");

        for (MemoryCategory category : MemoryCategory.values()) {
            int count = (int) entries.stream().filter(e -> e.getCategory() == category).count();
            if (count > 0) {
                sb.append(category).append(": ").append(count).append("\n");
            }
        }
        return sb.toString();
    }

    public String formatForSystemInjection(int maxEntries) {
        synchronized (lock) {
            List<MemoryEntry> important = getTopImportant(maxEntries);
            if (important.isEmpty()) return "";

            StringBuilder sb = new StringBuilder("\n\n=== PROJECT MEMORY (from previous sessions) ===\n");
            for (MemoryEntry e : important) {
                sb.append("- [").append(e.getCategory()).append("] ").append(e.getContent()).append("\n");
            }
            sb.append("=== END PROJECT MEMORY ===\n");
            return sb.toString();
        }
    }

    private void persist() {
        persistence.save(entries);
    }
}
