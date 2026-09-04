package com.example.myplugin.agent.debugging;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RedFlagDetectorTest {

    @Test
    void testDetectQuickFix() {
        List<String> flags = RedFlagDetector.detectRedFlags("Let me try a quick fix");
        assertFalse(flags.isEmpty());
    }

    @Test
    void testDetectJustTry() {
        List<String> flags = RedFlagDetector.detectRedFlags("Just try changing X");
        assertFalse(flags.isEmpty());
    }

    @Test
    void testDetectProbably() {
        List<String> flags = RedFlagDetector.detectRedFlags("It's probably X");
        assertFalse(flags.isEmpty());
    }

    @Test
    void testDetectSeeIfItWorks() {
        List<String> flags = RedFlagDetector.detectRedFlags("See if it works");
        assertFalse(flags.isEmpty());
    }

    @Test
    void testDetectSkipTest() {
        List<String> flags = RedFlagDetector.detectRedFlags("Skip the test, I'll manually verify");
        assertFalse(flags.isEmpty());
    }

    @Test
    void testDetectInvestigateLater() {
        List<String> flags = RedFlagDetector.detectRedFlags("Quick fix for now, investigate later");
        assertFalse(flags.isEmpty());
    }

    @Test
    void testDetectMultipleFlags() {
        List<String> flags = RedFlagDetector.detectRedFlags("Let me try a quick fix, see if it works");
        assertTrue(flags.size() >= 2);
    }

    @Test
    void testNoFlagsForCleanInput() {
        List<String> flags = RedFlagDetector.detectRedFlags("I will investigate the root cause systematically");
        assertTrue(flags.isEmpty());
    }

    @Test
    void testHasRedFlags() {
        assertTrue(RedFlagDetector.hasRedFlags("quick fix"));
        assertFalse(RedFlagDetector.hasRedFlags("systematic investigation"));
    }

    @Test
    void testGetRedFlagMessage() {
        List<String> flags = List.of("quick_fix", "just_try");
        String msg = RedFlagDetector.getRedFlagMessage(flags);
        assertTrue(msg.contains("RED FLAGS DETECTED"));
        assertTrue(msg.contains("STOP"));
    }

    @Test
    void testNullInput() {
        List<String> flags = RedFlagDetector.detectRedFlags(null);
        assertTrue(flags.isEmpty());
    }
}
