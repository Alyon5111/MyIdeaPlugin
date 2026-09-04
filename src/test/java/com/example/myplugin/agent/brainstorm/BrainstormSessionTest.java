package com.example.myplugin.agent.brainstorm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BrainstormSessionTest {

    @Test
    void createSession() {
        BrainstormSession session = new BrainstormSession("User Auth", "/project");

        assertEquals("User Auth", session.getTopic());
        assertEquals("/project", session.getProjectPath());
        assertEquals(BrainstormSession.Phase.EXPLORE_CONTEXT, session.getCurrentPhase());
        assertFalse(session.isCompleted());
        assertFalse(session.isCancelled());
    }

    @Test
    void addQuestion() {
        BrainstormSession session = new BrainstormSession("Feature", "/project");
        BrainstormSession.Question q = new BrainstormSession.Question(
            "What auth method?", List.of("OAuth", "JWT", "Session"), false);
        session.addQuestion(q);

        assertEquals(1, session.getQuestions().size());
        assertEquals("What auth method?", session.getCurrentQuestion().getText());
    }

    @Test
    void advanceQuestion() {
        BrainstormSession session = new BrainstormSession("Feature", "/project");
        session.addQuestion(new BrainstormSession.Question("Q1", null, false));
        session.addQuestion(new BrainstormSession.Question("Q2", null, false));

        assertEquals(0, session.getCurrentQuestionIndex());
        assertTrue(session.hasMoreQuestions());

        session.advanceQuestion();
        assertEquals(1, session.getCurrentQuestionIndex());
        assertTrue(session.hasMoreQuestions());

        session.advanceQuestion();
        assertFalse(session.hasMoreQuestions());
    }

    @Test
    void addApproach() {
        BrainstormSession session = new BrainstormSession("Feature", "/project");
        BrainstormSession.Approach a = new BrainstormSession.Approach(
            "Approach A", "Description", List.of("Pro1"), List.of("Con1"), true);
        session.addApproach(a);

        assertEquals(1, session.getApproaches().size());
        assertTrue(session.getApproaches().get(0).isRecommended());
    }

    @Test
    void addDesignSection() {
        BrainstormSession session = new BrainstormSession("Feature", "/project");
        BrainstormSession.DesignSection s = new BrainstormSession.DesignSection(
            "Architecture", "Content here", false);
        session.addDesignSection(s);

        assertEquals(1, session.getDesignSections().size());
        assertEquals("Architecture", session.getCurrentDesignSection().getTitle());
    }

    @Test
    void complete() {
        BrainstormSession session = new BrainstormSession("Feature", "/project");
        session.complete();

        assertTrue(session.isCompleted());
    }

    @Test
    void cancel() {
        BrainstormSession session = new BrainstormSession("Feature", "/project");
        session.cancel();

        assertTrue(session.isCancelled());
    }

    @Test
    void designDocFileName() {
        BrainstormSession session = new BrainstormSession("User Authentication", "/project");
        String fileName = session.getDesignDocFileName();

        assertTrue(fileName.endsWith("-design.md"));
        assertTrue(fileName.contains("user-authentication"));
    }

    @Test
    void questionWithOptions() {
        BrainstormSession.Question q = new BrainstormSession.Question(
            "Pick one", List.of("A", "B", "C"), false);

        assertTrue(q.hasOptions());
        assertEquals(3, q.getOptions().size());
        assertFalse(q.isMultiChoice());
    }
}
