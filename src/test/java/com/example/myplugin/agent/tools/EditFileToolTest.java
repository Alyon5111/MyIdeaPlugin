package com.example.myplugin.agent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EditFileToolTest {

    @Test
    void lfPatchIsConvertedToCrlfForCrlfFile() {
        String patch = "a\nb\nc";
        assertEquals("a\r\nb\r\nc", EditFileTool.normalizeSeparators(patch, "\r\n"));
    }

    @Test
    void crlfPatchIsCollapsedToLfForLfFile() {
        String patch = "a\r\nb\r\nc";
        assertEquals("a\nb\nc", EditFileTool.normalizeSeparators(patch, "\n"));
    }

    @Test
    void alreadyMatchingSeparatorsAreUnchanged() {
        assertEquals("a\nb", EditFileTool.normalizeSeparators("a\nb", "\n"));
        assertEquals("a\r\nb", EditFileTool.normalizeSeparators("a\r\nb", "\r\n"));
    }

    @Test
    void mixedSeparatorsAreNormalisedToTheTarget() {
        String mixed = "a\r\nb\nc";
        assertEquals("a\nb\nc", EditFileTool.normalizeSeparators(mixed, "\n"));
        assertEquals("a\r\nb\r\nc", EditFileTool.normalizeSeparators(mixed, "\r\n"));
    }
}
