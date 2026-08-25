package com.example.myplugin.openspec.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DeltaPlan {
    private final List<RequirementBlock> added;
    private final List<RequirementBlock> modified;
    private final List<String> removed;
    private final List<RenamePair> renamed;
    private final List<String> skippedHeaders;
    private final boolean hasAdded;
    private final boolean hasModified;
    private final boolean hasRemoved;
    private final boolean hasRenamed;

    public DeltaPlan(List<RequirementBlock> added, List<RequirementBlock> modified,
                     List<String> removed, List<RenamePair> renamed,
                     List<String> skippedHeaders,
                     boolean hasAdded, boolean hasModified,
                     boolean hasRemoved, boolean hasRenamed) {
        this.added = added != null ? Collections.unmodifiableList(added) : Collections.emptyList();
        this.modified = modified != null ? Collections.unmodifiableList(modified) : Collections.emptyList();
        this.removed = removed != null ? Collections.unmodifiableList(removed) : Collections.emptyList();
        this.renamed = renamed != null ? Collections.unmodifiableList(renamed) : Collections.emptyList();
        this.skippedHeaders = skippedHeaders != null ? Collections.unmodifiableList(skippedHeaders) : Collections.emptyList();
        this.hasAdded = hasAdded;
        this.hasModified = hasModified;
        this.hasRemoved = hasRemoved;
        this.hasRenamed = hasRenamed;
    }

    public List<RequirementBlock> getAdded() { return added; }
    public List<RequirementBlock> getModified() { return modified; }
    public List<String> getRemoved() { return removed; }
    public List<RenamePair> getRenamed() { return renamed; }
    public List<String> getSkippedHeaders() { return skippedHeaders; }
    public boolean hasAdded() { return hasAdded; }
    public boolean hasModified() { return hasModified; }
    public boolean hasRemoved() { return hasRemoved; }
    public boolean hasRenamed() { return hasRenamed; }

    public boolean isEmpty() {
        return added.isEmpty() && modified.isEmpty() && removed.isEmpty() && renamed.isEmpty();
    }

    @Override
    public String toString() {
        return "DeltaPlan{added=" + added.size() +
               ", modified=" + modified.size() +
               ", removed=" + removed.size() +
               ", renamed=" + renamed.size() + "}";
    }
}
