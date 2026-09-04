package com.example.myplugin.agent.subagent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReviewCoordinatorTest {

    @Test
    void testInitialState() {
        ReviewCoordinator coordinator = new ReviewCoordinator();
        assertEquals(ReviewCoordinator.ReviewStage.IDLE, coordinator.getCurrentStage());
        assertTrue(coordinator.getIssues().isEmpty());
    }

    @Test
    void testStartSpecReview() {
        ReviewCoordinator coordinator = new ReviewCoordinator();
        coordinator.startSpecReview("Implement feature");

        assertEquals(ReviewCoordinator.ReviewStage.SPEC_REVIEW, coordinator.getCurrentStage());
    }

    @Test
    void testSubmitSpecReviewPass() {
        ReviewCoordinator coordinator = new ReviewCoordinator();
        coordinator.startSpecReview("Implement feature");

        ReviewCoordinator.ReviewResult result = coordinator.submitSpecReview(List.of());
        assertEquals(ReviewCoordinator.ReviewResult.PASS, result);
        assertEquals(ReviewCoordinator.ReviewStage.CODE_QUALITY_REVIEW, coordinator.getCurrentStage());
    }

    @Test
    void testSubmitSpecReviewNeedsFixes() {
        ReviewCoordinator coordinator = new ReviewCoordinator();
        coordinator.startSpecReview("Implement feature");

        ReviewCoordinator.ReviewResult result = coordinator.submitSpecReview(List.of("Missing test", "Wrong API"));
        assertEquals(ReviewCoordinator.ReviewResult.NEEDS_FIXES, result);
        assertEquals(ReviewCoordinator.ReviewStage.RE_REVIEW, coordinator.getCurrentStage());
        assertEquals(2, coordinator.getIssues().size());
    }

    @Test
    void testStartCodeQualityReview() {
        ReviewCoordinator coordinator = new ReviewCoordinator();
        coordinator.startSpecReview("Implement feature");
        coordinator.submitSpecReview(List.of());

        coordinator.startCodeQualityReview();
        assertEquals(ReviewCoordinator.ReviewStage.CODE_QUALITY_REVIEW, coordinator.getCurrentStage());
    }

    @Test
    void testSubmitCodeQualityReviewPass() {
        ReviewCoordinator coordinator = new ReviewCoordinator();
        coordinator.startSpecReview("Implement feature");
        coordinator.submitSpecReview(List.of());
        coordinator.startCodeQualityReview();

        ReviewCoordinator.ReviewResult result = coordinator.submitCodeQualityReview(List.of());
        assertEquals(ReviewCoordinator.ReviewResult.PASS, result);
        assertTrue(coordinator.isComplete());
    }

    @Test
    void testSubmitCodeQualityReviewNeedsFixes() {
        ReviewCoordinator coordinator = new ReviewCoordinator();
        coordinator.startSpecReview("Implement feature");
        coordinator.submitSpecReview(List.of());
        coordinator.startCodeQualityReview();

        ReviewCoordinator.ReviewResult result = coordinator.submitCodeQualityReview(List.of("Magic number"));
        assertEquals(ReviewCoordinator.ReviewResult.NEEDS_FIXES, result);
        assertFalse(coordinator.isComplete());
    }

    @Test
    void testGetStatus() {
        ReviewCoordinator coordinator = new ReviewCoordinator();
        coordinator.startSpecReview("Implement feature");

        String status = coordinator.getStatus();
        assertTrue(status.contains("REVIEW STATUS"));
        assertTrue(status.contains("Stage: SPEC_REVIEW"));
    }
}
