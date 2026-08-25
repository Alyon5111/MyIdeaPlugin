package com.example.myplugin.openspec;

import com.example.myplugin.openspec.parser.CodeFenceMask;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CodeFenceMaskTest {

    @Test
    void noFences() {
        String[] lines = {"line1", "line2", "line3"};
        boolean[] mask = CodeFenceMask.build(lines);
        assertFalse(mask[0]);
        assertFalse(mask[1]);
        assertFalse(mask[2]);
    }

    @Test
    void simpleFence() {
        String[] lines = {
            "before",
            "```",
            "inside code",
            "```",
            "after"
        };
        boolean[] mask = CodeFenceMask.build(lines);
        assertFalse(mask[0]);
        assertTrue(mask[1]);   // opening fence
        assertTrue(mask[2]);   // inside
        assertTrue(mask[3]);   // closing fence
        assertFalse(mask[4]);
    }

    @Test
    void nestedBackticks() {
        String[] lines = {
            "```",
            "code with ``` inside",
            "```"
        };
        boolean[] mask = CodeFenceMask.build(lines);
        assertTrue(mask[0]);
        assertTrue(mask[1]);
        assertTrue(mask[2]);
    }

    @Test
    void tildesFence() {
        String[] lines = {
            "~~~",
            "inside",
            "~~~"
        };
        boolean[] mask = CodeFenceMask.build(lines);
        assertTrue(mask[0]);
        assertTrue(mask[1]);
        assertTrue(mask[2]);
    }

    @Test
    void emptyLines() {
        String[] lines = {"", "  ", ""};
        boolean[] mask = CodeFenceMask.build(lines);
        assertFalse(mask[0]);
        assertFalse(mask[1]);
        assertFalse(mask[2]);
    }

    @Test
    void fenceWithInfoString() {
        String[] lines = {
            "```java",
            "int x = 1;",
            "```"
        };
        boolean[] mask = CodeFenceMask.build(lines);
        assertTrue(mask[0]);
        assertTrue(mask[1]);
        assertTrue(mask[2]);
    }
}
