package com.example.myplugin.agent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WriteFileToolTest {

    @Test
    void newFileIsReportedAsNew() {
        assertEquals(" (new file, 12 chars)", WriteFileTool.describeWrite(null, "hello world!"));
    }

    @Test
    void identicalContentIsReportedAsNoChange() {
        assertTrue(WriteFileTool.describeWrite("same", "same").contains("no change"),
                WriteFileTool.describeWrite("same", "same"));
        assertTrue(WriteFileTool.describeWrite("same", "same").contains("identical content"));
    }

    @Test
    void overwriteOfExistingFileIsVisible() {
        String report = WriteFileTool.describeWrite("old content", "new content here");
        assertTrue(report.contains("OVERWROTE existing file"), report);
        assertTrue(report.contains("11 -> 16 chars"), report);
    }
}