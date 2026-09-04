package com.example.myplugin.agent.verification;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClaimDetectorTest {

    @Test
    void testDetectShouldWork() {
        List<String> claims = ClaimDetector.detectClaims("This should work now");
        assertFalse(claims.isEmpty());
    }

    @Test
    void testDetectProbably() {
        List<String> claims = ClaimDetector.detectClaims("Probably fixed");
        assertFalse(claims.isEmpty());
    }

    @Test
    void testDetectSeemsTo() {
        List<String> claims = ClaimDetector.detectClaims("Seems to be working");
        assertFalse(claims.isEmpty());
    }

    @Test
    void testDetectTestsPass() {
        List<String> claims = ClaimDetector.detectClaims("All tests pass");
        assertFalse(claims.isEmpty());
    }

    @Test
    void testDetectDone() {
        List<String> claims = ClaimDetector.detectClaims("Done!");
        assertFalse(claims.isEmpty());
    }

    @Test
    void testDetectComplete() {
        List<String> claims = ClaimDetector.detectClaims("Task complete");
        assertFalse(claims.isEmpty());
    }

    @Test
    void testDetectFixed() {
        List<String> claims = ClaimDetector.detectClaims("Bug fixed");
        assertFalse(claims.isEmpty());
    }

    @Test
    void testDetectSuccess() {
        List<String> claims = ClaimDetector.detectClaims("Success!");
        assertFalse(claims.isEmpty());
    }

    @Test
    void testNoClaimsForCleanInput() {
        List<String> claims = ClaimDetector.detectClaims("I need to investigate further");
        assertTrue(claims.isEmpty());
    }

    @Test
    void testHasClaims() {
        assertTrue(ClaimDetector.hasClaims("tests pass"));
        assertFalse(ClaimDetector.hasClaims("investigating"));
    }

    @Test
    void testGetClaimMessage() {
        List<String> claims = List.of("should_work", "tests_pass");
        String msg = ClaimDetector.getClaimMessage(claims);
        assertTrue(msg.contains("CLAIMS DETECTED WITHOUT VERIFICATION"));
        assertTrue(msg.contains("VERIFICATION REQUIRED"));
    }

    @Test
    void testNullInput() {
        List<String> claims = ClaimDetector.detectClaims(null);
        assertTrue(claims.isEmpty());
    }
}
