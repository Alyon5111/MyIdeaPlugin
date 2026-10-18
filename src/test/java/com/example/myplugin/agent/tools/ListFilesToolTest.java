package com.example.myplugin.agent.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListFilesToolTest {

    @Test
    void listsOneLevelByDefault(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("src/main/java"));
        Files.writeString(root.resolve("src/keep.txt"), "a");
        Files.writeString(root.resolve("top.txt"), "b");

        String out = ListFilesTool.list(root, "", false, 40);

        assertTrue(out.contains("src/"), out);
        assertTrue(out.contains("top.txt"), out);
        // The nested file belongs to the second level and must wait for recursive=true.
        assertFalse(out.contains("keep.txt"), out);
        assertFalse(out.contains("main/"), out);
    }

    @Test
    void recursiveFlagDescendsIntoSubdirectories(@TempDir Path root) throws IOException {
        Path nested = root.resolve("a/b");
        Files.createDirectories(nested);
        Files.writeString(nested.resolve("deep.txt"), "x");

        String shallow = ListFilesTool.list(root, "", false, 40);
        String deep = ListFilesTool.list(root, "", true, 40);

        assertFalse(shallow.contains("deep.txt"), shallow);
        assertTrue(deep.contains("deep.txt"), deep);
    }

    @Test
    void entryCapTruncatesAndSaysHowToGetMore(@TempDir Path root) throws IOException {
        for (int i = 0; i < 12; i++) {
            Files.writeString(root.resolve("file" + i + ".txt"), "x");
        }

        String out = ListFilesTool.list(root, "", false, 5);

        assertTrue(out.contains("more entries not shown"), out);
        assertTrue(out.contains("narrow the path"), out);
        assertTrue(out.contains("recursive=true"), out);
    }

    @Test
    void buildDirectoryIsSkipped(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("build/test-results"));
        Files.writeString(root.resolve("build/test-results/out.xml"), "<testsuite/>");
        Files.writeString(root.resolve("src.txt"), "x");

        String out = ListFilesTool.list(root, "", true, 40);

        assertFalse(out.contains("out.xml"), out);
        assertFalse(out.contains("build/"), out);
        assertTrue(out.contains("src.txt"), out);
    }

    @Test
    void missingPathReportsError(@TempDir Path root) {
        assertTrue(ListFilesTool.list(root, "nope", false, 40).contains("path not found"));
        assertTrue(ListFilesTool.excludedName("build"));
        assertFalse(ListFilesTool.excludedName("src"));
    }
}