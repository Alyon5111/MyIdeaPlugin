package com.example.myplugin.openspec.parser;

/**
 * Builds a boolean mask marking lines that are inside fenced code blocks.
 * Lines inside ``` blocks should be ignored for structural Markdown parsing.
 */
public class CodeFenceMask {

    private CodeFenceMask() {}

    /**
     * Build a mask where mask[i] = true means lines[i] is inside (or is) a fence delimiter.
     */
    public static boolean[] build(String[] lines) {
        boolean[] mask = new boolean[lines.length];
        boolean inFence = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (isFenceDelimiter(line)) {
                mask[i] = true;
                inFence = !inFence;
            } else {
                mask[i] = inFence;
            }
        }
        return mask;
    }

    /**
     * A fence delimiter is a line starting with 3+ backticks or tildes,
     * optionally followed by content (info string).
     */
    private static boolean isFenceDelimiter(String line) {
        if (line == null) return false;
        String trimmed = line.replaceAll("\\s+$", "");
        if (trimmed.isEmpty()) return false;
        char fenceChar = trimmed.charAt(0);
        if (fenceChar != '`' && fenceChar != '~') return false;
        int count = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            if (trimmed.charAt(i) == fenceChar) {
                count++;
            } else {
                break;
            }
        }
        return count >= 3;
    }
}
