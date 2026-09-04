package com.example.myplugin.agent.tdd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RedFlagDetectorTest {

    @Test
    void noRationalization() {
        RedFlagDetector detector = new RedFlagDetector();
        detector.detectRationalization("Let's implement the login feature");

        assertFalse(detector.hasRedFlags());
    }

    @Test
    void detectTooSimple() {
        RedFlagDetector detector = new RedFlagDetector();
        detector.detectRationalization("This is too simple, don't need test");

        assertTrue(detector.hasRedFlags());
        assertTrue(detector.getRedFlags().get(0).contains("too simple"));
    }

    @Test
    void detectSkipTest() {
        RedFlagDetector detector = new RedFlagDetector();
        detector.detectRationalization("I'll skip test for now");

        assertTrue(detector.hasRedFlags());
    }

    @Test
    void detectJustThisOnce() {
        RedFlagDetector detector = new RedFlagDetector();
        detector.detectRationalization("Just this once, no test");

        assertTrue(detector.hasRedFlags());
    }

    @Test
    void detectManualTest() {
        RedFlagDetector detector = new RedFlagDetector();
        detector.detectRationalization("I did a manual test already");

        assertTrue(detector.hasRedFlags());
    }

    @Test
    void detectCodeBeforeTest() {
        RedFlagDetector detector = new RedFlagDetector();
        detector.detectCodeBeforeTest("write_file:UserService.java edit_file:UserService.java");

        assertTrue(detector.hasRedFlags());
    }

    @Test
    void detectTestImmediatelyPassing() {
        RedFlagDetector detector = new RedFlagDetector();
        detector.detectTestImmediatelyPassing("BUILD SUCCESSFUL\n1 test passed");

        assertTrue(detector.hasRedFlags());
    }

    @Test
    void clearFlags() {
        RedFlagDetector detector = new RedFlagDetector();
        detector.detectRationalization("too simple");
        assertTrue(detector.hasRedFlags());

        detector.clear();
        assertFalse(detector.hasRedFlags());
    }

    @Test
    void generateReport_empty() {
        RedFlagDetector detector = new RedFlagDetector();
        String report = detector.generateReport();

        assertTrue(report.contains("No red flags"));
    }

    @Test
    void generateReport_withFlags() {
        RedFlagDetector detector = new RedFlagDetector();
        detector.detectRationalization("skip test");
        String report = detector.generateReport();

        assertTrue(report.contains("Red Flags Detected"));
    }
}
