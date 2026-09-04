package com.example.myplugin.agent.tdd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestAnalyzerTest {

    @Test
    void analyzeBuildSuccessfulWithPassedTests() {
        String output = "> Task :test\nBUILD SUCCESSFUL in 2s\n10 tests passed";
        TestAnalyzer.Analysis analysis = TestAnalyzer.analyze(output);

        assertEquals(TestAnalyzer.Outcome.TEST_PASSES, analysis.getOutcome());
        assertTrue(analysis.isPass());
    }

    @Test
    void analyzeBuildFailedWithTestFailure() {
        String output = "> Task :test FAILED\nBUILD FAILED\nTestAuthentication: Expected true but was false";
        TestAnalyzer.Analysis analysis = TestAnalyzer.analyze(output);

        assertEquals(TestAnalyzer.Outcome.TEST_FAILS_AS_EXPECTED, analysis.getOutcome());
        assertTrue(analysis.isExpectedFailure());
    }

    @Test
    void analyzeBuildFailedWithClassNotFound() {
        String output = "> Task :test FAILED\nBUILD FAILED\nerror: class not found: UserService";
        TestAnalyzer.Analysis analysis = TestAnalyzer.analyze(output);

        assertEquals(TestAnalyzer.Outcome.TEST_FAILS_AS_EXPECTED, analysis.getOutcome());
    }

    @Test
    void analyzeBuildFailedWithCompilationError() {
        String output = "> Task :compileJava FAILED\nBUILD FAILED\ncompilation error: cannot find symbol";
        TestAnalyzer.Analysis analysis = TestAnalyzer.analyze(output);

        assertEquals(TestAnalyzer.Outcome.TEST_ERROR, analysis.getOutcome());
        assertTrue(analysis.isError());
    }

    @Test
    void analyzeBuildFailedWithNullPointer() {
        String output = "> Task :test FAILED\nBUILD FAILED\njava.lang.NullPointerException at Test.java:42";
        TestAnalyzer.Analysis analysis = TestAnalyzer.analyze(output);

        assertEquals(TestAnalyzer.Outcome.TEST_CRASH, analysis.getOutcome());
        assertTrue(analysis.isError());
    }

    @Test
    void analyzeEmptyOutput() {
        TestAnalyzer.Analysis analysis = TestAnalyzer.analyze("");

        assertEquals(TestAnalyzer.Outcome.UNKNOWN, analysis.getOutcome());
    }

    @Test
    void analyzeNullOutput() {
        TestAnalyzer.Analysis analysis = TestAnalyzer.analyze(null);

        assertEquals(TestAnalyzer.Outcome.UNKNOWN, analysis.getOutcome());
    }

    @Test
    void analyzeBuildSuccessfulNoTests() {
        String output = "> Task :test\nBUILD SUCCESSFUL in 1s";
        TestAnalyzer.Analysis analysis = TestAnalyzer.analyze(output);

        assertEquals(TestAnalyzer.Outcome.TEST_PASSES, analysis.getOutcome());
    }

    @Test
    void analyzeBuildFailedGeneric() {
        String output = "> Task :test FAILED\nBUILD FAILED";
        TestAnalyzer.Analysis analysis = TestAnalyzer.analyze(output);

        assertEquals(TestAnalyzer.Outcome.TEST_FAILS_AS_EXPECTED, analysis.getOutcome());
    }
}
