package com.example.myplugin.agent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchCodeToolTest {

    @Test
    void buildOutputAndVcsMetadataAreExcluded() {
        assertTrue(SearchCodeTool.excludedDirName("build"));
        assertTrue(SearchCodeTool.excludedDirName(".git"));
        assertTrue(SearchCodeTool.excludedDirName(".gradle"));
        assertTrue(SearchCodeTool.excludedDirName(".idea"));
        assertTrue(SearchCodeTool.excludedDirName("node_modules"));
        assertTrue(SearchCodeTool.excludedDirName("target"));
        assertTrue(SearchCodeTool.excludedDirName("out"));
    }

    @Test
    void exclusionIsCaseInsensitive() {
        assertTrue(SearchCodeTool.excludedDirName("BUILD"));
        assertTrue(SearchCodeTool.excludedDirName("Build"));
    }

    @Test
    void sourceDirectoriesAreSearched() {
        assertFalse(SearchCodeTool.excludedDirName("src"));
        assertFalse(SearchCodeTool.excludedDirName("main"));
        assertFalse(SearchCodeTool.excludedDirName("java"));
        assertFalse(SearchCodeTool.excludedDirName("com"));
    }
}