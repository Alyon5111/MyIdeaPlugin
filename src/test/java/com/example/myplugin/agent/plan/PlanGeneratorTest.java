package com.example.myplugin.agent.plan;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

class PlanGeneratorTest {

    private final PlanGenerator generator = new PlanGenerator();

    @Test
    void getPlanFileNameForAsciiSlug() {
        String name = generator.getPlanFileName("export plan tool");
        assertEquals(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "-export-plan-tool-plan.md", name);
    }

    @Test
    void getPlanFileNameForChineseSlugFallsBackToPlan() {
        // "Add Agent tool export_plan, read docs/plans plan and export Markdown to docs/plans/export" written in Chinese
        String chinese = "\u65b0\u589e Agent \u5de5\u5177 export_plan\uff0c\u8bfb\u53d6 docs/plans \u8ba1\u5212\u5e76\u5bfc\u51fa\u4e3a Markdown \u5230 docs/plans/export";
        String name = generator.getPlanFileName(chinese);
        assertTrue(name.startsWith(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "-"), name);
        assertTrue(name.endsWith("-plan.md"), name);
        assertFalse(name.matches(".*[\\u4e00-\\u9fa5].*"), "must not contain Chinese chars: " + name);
        assertFalse(name.length() > 120, "must be reasonably short: " + name);
        assertFalse(name.contains("\uff0c"), name);
        assertFalse(name.contains(" "), name);
    }

    @Test
    void getPlanFileNameForNullOrEmpty() {
        assertTrue(generator.getPlanFileName(null).endsWith("-plan.md"));
        assertTrue(generator.getPlanFileName("").endsWith("-plan.md"));
    }

    @Test
    void getPlanFileNameTruncatesLongSlug() {
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longName.append("abcdefghij");
        }
        String name = generator.getPlanFileName(longName.toString());
        assertTrue(name.length() < 120, "must truncate over-long slugs: " + name.length());
    }
}