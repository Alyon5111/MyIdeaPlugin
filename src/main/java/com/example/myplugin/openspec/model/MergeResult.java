package com.example.myplugin.openspec.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MergeResult {
    private final String rebuilt;
    private final boolean retired;
    private final List<String> warnings;
    private final List<String> unaccountedContent;
    private final Counts counts;

    public static class Counts {
        private final int added;
        private final int modified;
        private final int removed;
        private final int renamed;

        public Counts(int added, int modified, int removed, int renamed) {
            this.added = added;
            this.modified = modified;
            this.removed = removed;
            this.renamed = renamed;
        }

        public int getAdded() { return added; }
        public int getModified() { return modified; }
        public int getRemoved() { return removed; }
        public int getRenamed() { return renamed; }

        public int getTotal() { return added + modified + removed + renamed; }
    }

    public MergeResult(String rebuilt, boolean retired, List<String> warnings,
                       List<String> unaccountedContent, Counts counts) {
        this.rebuilt = rebuilt;
        this.retired = retired;
        this.warnings = warnings != null ? Collections.unmodifiableList(warnings) : Collections.emptyList();
        this.unaccountedContent = unaccountedContent != null ? Collections.unmodifiableList(unaccountedContent) : Collections.emptyList();
        this.counts = counts;
    }

    public String getRebuilt() { return rebuilt; }
    public boolean isRetired() { return retired; }
    public List<String> getWarnings() { return warnings; }
    public List<String> getUnaccountedContent() { return unaccountedContent; }
    public Counts getCounts() { return counts; }

    @Override
    public String toString() {
        return "MergeResult{retired=" + retired +
               ", counts=" + counts +
               ", warnings=" + warnings.size() + "}";
    }
}
