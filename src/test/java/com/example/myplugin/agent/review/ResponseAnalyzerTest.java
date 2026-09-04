package com.example.myplugin.agent.review;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResponseAnalyzerTest {

    @Test
    void testDetectYoureAbsolutelyRight() {
        List<String> responses = ResponseAnalyzer.detectPerformativeResponses("You're absolutely right!");
        assertFalse(responses.isEmpty());
    }

    @Test
    void testDetectGreatPoint() {
        List<String> responses = ResponseAnalyzer.detectPerformativeResponses("Great point!");
        assertFalse(responses.isEmpty());
    }

    @Test
    void testDetectExcellentFeedback() {
        List<String> responses = ResponseAnalyzer.detectPerformativeResponses("Excellent feedback!");
        assertFalse(responses.isEmpty());
    }

    @Test
    void testDetectThanksFor() {
        List<String> responses = ResponseAnalyzer.detectPerformativeResponses("Thanks for catching that!");
        assertFalse(responses.isEmpty());
    }

    @Test
    void testDetectGoodCatch() {
        List<String> responses = ResponseAnalyzer.detectPerformativeResponses("Good catch!");
        assertFalse(responses.isEmpty());
    }

    @Test
    void testNoPerformativeForTechnicalResponse() {
        List<String> responses = ResponseAnalyzer.detectPerformativeResponses("Fixed. Added null check.");
        assertTrue(responses.isEmpty());
    }

    @Test
    void testHasPerformativeResponses() {
        assertTrue(ResponseAnalyzer.hasPerformativeResponses("You're absolutely right!"));
        assertFalse(ResponseAnalyzer.hasPerformativeResponses("Fixed the bug."));
    }

    @Test
    void testGetPerformativeMessage() {
        List<String> responses = List.of("youre_absolutely_right");
        String msg = ResponseAnalyzer.getPerformativeMessage(responses);
        assertTrue(msg.contains("PERFORMATIVE RESPONSES DETECTED"));
        assertTrue(msg.contains("INSTEAD"));
    }

    @Test
    void testIsYagniCandidate() {
        assertTrue(ResponseAnalyzer.isYagniCandidate("implement proper metrics"));
        assertTrue(ResponseAnalyzer.isYagniCandidate("real production system"));
        assertFalse(ResponseAnalyzer.isYagniCandidate("add null check"));
    }

    @Test
    void testGetYagniMessage() {
        String msg = ResponseAnalyzer.getYagniMessage("proper metrics");
        assertTrue(msg.contains("YAGNI CHECK"));
        assertTrue(msg.contains("Is this actually used?"));
    }

    @Test
    void testNullInput() {
        List<String> responses = ResponseAnalyzer.detectPerformativeResponses(null);
        assertTrue(responses.isEmpty());
    }
}
