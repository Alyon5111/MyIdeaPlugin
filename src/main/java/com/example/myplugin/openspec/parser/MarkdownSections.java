package com.example.myplugin.openspec.parser;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a section of a Markdown document split by ## headers.
 */
public class MarkdownSections {

    public static class SectionBody {
        private final String[] lines;
        private final boolean[] fenceMask;
        private final int bodyStartLine;

        public SectionBody(String[] lines, boolean[] fenceMask, int bodyStartLine) {
            this.lines = lines;
            this.fenceMask = fenceMask;
            this.bodyStartLine = bodyStartLine;
        }

        public String[] getLines() { return lines; }
        public boolean[] getFenceMask() { return fenceMask; }
        public int getBodyStartLine() { return bodyStartLine; }
        public boolean isEmpty() { return lines.length == 0; }
    }

    private static final SectionBody EMPTY = new SectionBody(new String[0], new boolean[0], 0);

    private MarkdownSections() {}

    /**
     * Split content into top-level ## sections.
     * Returns a map of section title (without ## prefix) to body.
     */
    public static Map<String, SectionBody> splitTopLevelSections(String content) {
        String normalized = normalizeLineEndings(content);
        String[] lines = normalized.split("\n");
        boolean[] mask = CodeFenceMask.build(lines);

        Map<String, SectionBody> result = new LinkedHashMap<>();
        java.util.List<int[]> indices = new java.util.ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            if (mask[i]) continue;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^##\\s+(.+)$").matcher(lines[i]);
            if (m.find()) {
                indices.add(new int[]{i, result.size()});
                result.put(m.group(1).trim(), null); // placeholder
            }
        }

        java.util.List<String> titles = new java.util.ArrayList<>(result.keySet());
        result.clear();

        for (int i = 0; i < indices.size(); i++) {
            int start = indices.get(i)[0];
            int end = (i + 1 < indices.size()) ? indices.get(i + 1)[0] : lines.length;
            String[] sectionLines = java.util.Arrays.copyOfRange(lines, start + 1, end);
            boolean[] sectionMask = java.util.Arrays.copyOfRange(mask, start + 1, end);
            result.put(titles.get(i), new SectionBody(sectionLines, sectionMask, start + 2));
        }

        return result;
    }

    /**
     * Find a section case-insensitively.
     */
    public static SectionBody findSection(Map<String, SectionBody> sections, String desired) {
        String target = desired.toLowerCase();
        for (Map.Entry<String, SectionBody> entry : sections.entrySet()) {
            if (entry.getKey().toLowerCase().equals(target)) {
                return entry.getValue() != null ? entry.getValue() : EMPTY;
            }
        }
        return EMPTY;
    }

    /**
     * Find a section case-insensitively, returning title, body, and whether it was found.
     */
    public static SectionMatch findSectionWithMeta(Map<String, SectionBody> sections, String desired) {
        String target = desired.toLowerCase();
        for (Map.Entry<String, SectionBody> entry : sections.entrySet()) {
            if (entry.getKey().toLowerCase().equals(target)) {
                SectionBody body = entry.getValue() != null ? entry.getValue() : EMPTY;
                return new SectionMatch(entry.getKey(), body, true);
            }
        }
        return new SectionMatch(desired, EMPTY, false);
    }

    public static class SectionMatch {
        private final String title;
        private final SectionBody body;
        private final boolean found;

        public SectionMatch(String title, SectionBody body, boolean found) {
            this.title = title;
            this.body = body;
            this.found = found;
        }

        public String getTitle() { return title; }
        public SectionBody getBody() { return body; }
        public boolean isFound() { return found; }
    }

    public static String normalizeLineEndings(String content) {
        if (content == null) return "";
        // Strip UTF-8 BOM
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }
        return content.replace("\r\n", "\n").replace("\r", "\n");
    }
}
