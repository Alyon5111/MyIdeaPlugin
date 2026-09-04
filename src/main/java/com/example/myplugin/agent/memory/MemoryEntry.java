package com.example.myplugin.agent.memory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MemoryEntry {

    private String id;
    private MemoryCategory category;
    private String content;
    private String source;
    private double confidence;
    private List<String> tags = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime lastAccessedAt;
    private int accessCount;

    private MemoryEntry() {}

    public MemoryEntry(MemoryCategory category, String content, String source, double confidence) {
        this.id = UUID.randomUUID().toString();
        this.category = category;
        this.content = content;
        this.source = source;
        this.confidence = confidence;
        this.createdAt = LocalDateTime.now();
        this.lastAccessedAt = createdAt;
        this.accessCount = 0;
    }

    public String getId() { return id; }
    public MemoryCategory getCategory() { return category; }
    public String getContent() { return content; }
    public String getSource() { return source; }
    public double getConfidence() { return confidence; }
    public List<String> getTags() { return tags; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastAccessedAt() { return lastAccessedAt; }
    public int getAccessCount() { return accessCount; }

    public void setContent(String content) { this.content = content; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public void setCategory(MemoryCategory category) { this.category = category; }

    public void addTag(String tag) {
        if (!tags.contains(tag)) {
            tags.add(tag);
        }
    }

    public void recordAccess() {
        this.accessCount++;
        this.lastAccessedAt = LocalDateTime.now();
    }

    public double getRecencyScore(long nowEpochMillis) {
        long accessMillis = lastAccessedAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        long ageMillis = nowEpochMillis - accessMillis;
        return Math.max(0, 1.0 - (double) ageMillis / (30L * 24 * 3600 * 1000));
    }

    public double getImportanceScore(long nowEpochMillis) {
        double recency = getRecencyScore(nowEpochMillis);
        double frequency = Math.min(1.0, accessCount / 10.0);
        return 0.6 * recency + 0.4 * frequency;
    }
}
