package com.example.myplugin.agent.brainstorm;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class BrainstormSession {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public enum Phase {
        EXPLORE_CONTEXT,
        ASK_QUESTIONS,
        PROPOSE_APPROACHES,
        PRESENT_DESIGN,
        WRITE_DOC,
        SELF_REVIEW,
        WAIT_APPROVAL,
        COMPLETED,
        CANCELLED
    }

    private final String topic;
    private final String projectPath;
    private Phase currentPhase;
    private final List<Question> questions;
    private final List<String> userAnswers;
    private final List<Approach> approaches;
    private final List<DesignSection> designSections;
    private String selectedApproach;
    private String designDocPath;
    private int currentQuestionIndex;
    private int currentDesignSectionIndex;

    public BrainstormSession(String topic, String projectPath) {
        this.topic = topic;
        this.projectPath = projectPath;
        this.currentPhase = Phase.EXPLORE_CONTEXT;
        this.questions = new ArrayList<>();
        this.userAnswers = new ArrayList<>();
        this.approaches = new ArrayList<>();
        this.designSections = new ArrayList<>();
        this.currentQuestionIndex = 0;
        this.currentDesignSectionIndex = 0;
    }

    public String getTopic() { return topic; }
    public String getProjectPath() { return projectPath; }
    public Phase getCurrentPhase() { return currentPhase; }
    public void setCurrentPhase(Phase phase) { this.currentPhase = phase; }

    public List<Question> getQuestions() { return questions; }
    public void addQuestion(Question q) { questions.add(q); }

    public List<String> getUserAnswers() { return userAnswers; }
    public void addUserAnswer(String answer) { userAnswers.add(answer); }

    public List<Approach> getApproaches() { return approaches; }
    public void addApproach(Approach a) { approaches.add(a); }

    public List<DesignSection> getDesignSections() { return designSections; }
    public void addDesignSection(DesignSection s) { designSections.add(s); }

    public String getSelectedApproach() { return selectedApproach; }
    public void setSelectedApproach(String selected) { this.selectedApproach = selected; }

    public String getDesignDocPath() { return designDocPath; }
    public void setDesignDocPath(String path) { this.designDocPath = path; }

    public int getCurrentQuestionIndex() { return currentQuestionIndex; }
    public void setCurrentQuestionIndex(int idx) { this.currentQuestionIndex = idx; }

    public int getCurrentDesignSectionIndex() { return currentDesignSectionIndex; }
    public void setCurrentDesignSectionIndex(int idx) { this.currentDesignSectionIndex = idx; }

    public boolean hasMoreQuestions() { return currentQuestionIndex < questions.size(); }
    public boolean hasMoreDesignSections() { return currentDesignSectionIndex < designSections.size(); }

    public Question getCurrentQuestion() {
        if (currentQuestionIndex >= questions.size()) return null;
        return questions.get(currentQuestionIndex);
    }

    public DesignSection getCurrentDesignSection() {
        if (currentDesignSectionIndex >= designSections.size()) return null;
        return designSections.get(currentDesignSectionIndex);
    }

    public void advanceQuestion() { currentQuestionIndex++; }

    public void advanceDesignSection() { currentDesignSectionIndex++; }

    public String getDesignDocFileName() {
        String slug = topic.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return LocalDate.now().format(DATE_FMT) + "-" + slug + "-design.md";
    }

    public boolean isCompleted() { return currentPhase == Phase.COMPLETED; }
    public boolean isCancelled() { return currentPhase == Phase.CANCELLED; }

    public void cancel() { this.currentPhase = Phase.CANCELLED; }
    public void complete() { this.currentPhase = Phase.COMPLETED; }

    public static class Question {
        private final String text;
        private final List<String> options;
        private final boolean multiChoice;

        public Question(String text, List<String> options, boolean multiChoice) {
            this.text = text;
            this.options = options != null ? options : new ArrayList<>();
            this.multiChoice = multiChoice;
        }

        public String getText() { return text; }
        public List<String> getOptions() { return options; }
        public boolean isMultiChoice() { return multiChoice; }
        public boolean hasOptions() { return !options.isEmpty(); }
    }

    public static class Approach {
        private final String name;
        private final String description;
        private final List<String> pros;
        private final List<String> cons;
        private final boolean recommended;

        public Approach(String name, String description, List<String> pros, List<String> cons, boolean recommended) {
            this.name = name;
            this.description = description;
            this.pros = pros != null ? pros : new ArrayList<>();
            this.cons = cons != null ? cons : new ArrayList<>();
            this.recommended = recommended;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public List<String> getPros() { return pros; }
        public List<String> getCons() { return cons; }
        public boolean isRecommended() { return recommended; }
    }

    public static class DesignSection {
        private final String title;
        private final String content;
        private final boolean approved;

        public DesignSection(String title, String content, boolean approved) {
            this.title = title;
            this.content = content;
            this.approved = approved;
        }

        public String getTitle() { return title; }
        public String getContent() { return content; }
        public boolean isApproved() { return approved; }
    }
}
