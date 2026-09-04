package com.example.myplugin.agent.brainstorm;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentTool;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BrainstormTool implements AgentTool {

    private final AgentContext context;
    private final Map<String, BrainstormSession> sessions = new HashMap<>();
    private final DesignDocGenerator docGenerator = new DesignDocGenerator();

    public BrainstormTool(AgentContext context) {
        this.context = context;
    }

    @Override
    public String name() { return "brainstorm"; }

    @Override
    public String description() {
        return "Start a design brainstorming session. Use this when the user wants to design a new feature or make architectural decisions before writing code.";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
            .name(name())
            .description(description())
            .parameters(JsonObjectSchema.builder()
                .addProperty("action", JsonStringSchema.builder()
                    .description("Action to perform: start, answer_question, select_approach, approve_design, cancel")
                    .build())
                .addProperty("topic", JsonStringSchema.builder()
                    .description("Topic or feature to brainstorm (required for start)")
                    .build())
                .addProperty("answer", JsonStringSchema.builder()
                    .description("Answer to current question (required for answer_question)")
                    .build())
                .addProperty("approach", JsonStringSchema.builder()
                    .description("Selected approach name (required for select_approach)")
                    .build())
                .build())
            .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        try {
            String action = arguments.has("action") ? arguments.get("action").getAsString() : "start";

            switch (action) {
                case "start":
                    return startSession(arguments);
                case "get_status":
                    return getStatus();
                case "answer_question":
                    return answerQuestion(arguments);
                case "select_approach":
                    return selectApproach(arguments);
                case "approve_design":
                    return approveDesign();
                case "cancel":
                    return cancelSession();
                default:
                    return "Error: Unknown action '" + action + "'. Valid actions: start, get_status, answer_question, select_approach, approve_design, cancel";
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String startSession(JsonObject arguments) {
        String topic = arguments.has("topic") ? arguments.get("topic").getAsString() : null;
        if (topic == null || topic.trim().isEmpty()) {
            return "Error: 'topic' is required for start action";
        }

        BrainstormSession session = new BrainstormSession(topic, context.getProject().getBasePath());
        sessions.put("active", session);

        session.setCurrentPhase(BrainstormSession.Phase.EXPLORE_CONTEXT);

        StringBuilder sb = new StringBuilder();
        sb.append("=== Brainstorming Session Started ===\n");
        sb.append("Topic: ").append(topic).append("\n\n");
        sb.append("Phase 1: Explore Context\n");
        sb.append("I'll analyze the project structure and recent changes to understand the context.\n\n");
        sb.append("Phase 2: Ask Questions\n");
        sb.append("I'll ask you clarifying questions one at a time to understand your requirements.\n");
        sb.append("Prefer multiple choice answers when available.\n\n");
        sb.append("Phase 3: Propose Approaches\n");
        sb.append("I'll present 2-3 approaches with pros/cons and a recommendation.\n\n");
        sb.append("Phase 4: Present Design\n");
        sb.append("I'll present the design in sections (Architecture, Components, Data Flow, Error Handling, Testing).\n");
        sb.append("Each section requires your approval before proceeding.\n\n");
        sb.append("Phase 5: Write Design Doc\n");
        sb.append("I'll generate a design document at docs/specs/" + session.getDesignDocFileName() + "\n\n");
        sb.append("Phase 6: Self-Review\n");
        sb.append("I'll check for placeholders, inconsistencies, and ambiguities.\n\n");
        sb.append("Phase 7: Wait for Approval\n");
        sb.append("HARD GATE: You must approve the design before any code is written.\n\n");
        sb.append("=== Ready to begin. Analyzing project context... ===");

        return sb.toString();
    }

    private String getStatus() {
        BrainstormSession session = sessions.get("active");
        if (session == null) {
            return "No active brainstorming session. Use 'start' to begin one.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== Brainstorming Session Status ===\n");
        sb.append("Topic: ").append(session.getTopic()).append("\n");
        sb.append("Phase: ").append(session.getCurrentPhase()).append("\n\n");

        switch (session.getCurrentPhase()) {
            case EXPLORE_CONTEXT:
                sb.append("Analyzing project context...\n");
                sb.append("Next: Will ask clarifying questions.\n");
                break;
            case ASK_QUESTIONS:
                sb.append("Question ").append(session.getCurrentQuestionIndex() + 1)
                  .append("/").append(session.getQuestions().size()).append("\n");
                if (session.hasMoreQuestions()) {
                    BrainstormSession.Question q = session.getCurrentQuestion();
                    sb.append("Current question: ").append(q.getText()).append("\n");
                    if (q.hasOptions()) {
                        sb.append("Options:\n");
                        for (int i = 0; i < q.getOptions().size(); i++) {
                            sb.append("  ").append(i + 1).append(". ").append(q.getOptions().get(i)).append("\n");
                        }
                    }
                }
                break;
            case PROPOSE_APPROACHES:
                sb.append("Available approaches:\n");
                for (BrainstormSession.Approach a : session.getApproaches()) {
                    sb.append("  - ").append(a.getName());
                    if (a.isRecommended()) sb.append(" (RECOMMENDED)");
                    sb.append("\n");
                }
                break;
            case PRESENT_DESIGN:
                sb.append("Design section ").append(session.getCurrentDesignSectionIndex() + 1)
                  .append("/").append(session.getDesignSections().size()).append("\n");
                break;
            case WAIT_APPROVAL:
                sb.append("Waiting for your approval.\n");
                sb.append("Respond with 'approve_design' to approve or describe changes needed.\n");
                break;
            default:
                break;
        }

        return sb.toString();
    }

    private String answerQuestion(JsonObject arguments) {
        BrainstormSession session = sessions.get("active");
        if (session == null) return "Error: No active session. Start one first.";
        if (session.getCurrentPhase() != BrainstormSession.Phase.ASK_QUESTIONS) {
            return "Error: Not in ASK_QUESTIONS phase. Current phase: " + session.getCurrentPhase();
        }

        String answer = arguments.has("answer") ? arguments.get("answer").getAsString() : null;
        if (answer == null || answer.trim().isEmpty()) {
            return "Error: 'answer' is required";
        }

        session.addUserAnswer(answer);
        session.advanceQuestion();

        StringBuilder sb = new StringBuilder();
        sb.append("Answer recorded: ").append(answer).append("\n\n");

        if (session.hasMoreQuestions()) {
            BrainstormSession.Question q = session.getCurrentQuestion();
            sb.append("Next question (").append(session.getCurrentQuestionIndex() + 1)
              .append("/").append(session.getQuestions().size()).append("):\n");
            sb.append(q.getText()).append("\n");
            if (q.hasOptions()) {
                sb.append("\nOptions:\n");
                for (int i = 0; i < q.getOptions().size(); i++) {
                    sb.append("  ").append(i + 1).append(". ").append(q.getOptions().get(i)).append("\n");
                }
            }
        } else {
            sb.append("All questions answered. Moving to PROPOSE_APPROACHES phase.\n");
            sb.append("I'll now propose 2-3 approaches for your feature.\n");
            session.setCurrentPhase(BrainstormSession.Phase.PROPOSE_APPROACHES);
        }

        return sb.toString();
    }

    private String selectApproach(JsonObject arguments) {
        BrainstormSession session = sessions.get("active");
        if (session == null) return "Error: No active session.";
        if (session.getCurrentPhase() != BrainstormSession.Phase.PROPOSE_APPROACHES) {
            return "Error: Not in PROPOSE_APPROACHES phase.";
        }

        String approach = arguments.has("approach") ? arguments.get("approach").getAsString() : null;
        if (approach == null || approach.trim().isEmpty()) {
            return "Error: 'approach' is required";
        }

        boolean found = session.getApproaches().stream()
            .anyMatch(a -> a.getName().equalsIgnoreCase(approach));
        if (!found) {
            return "Error: Approach '" + approach + "' not found. Available: " +
                session.getApproaches().stream().map(BrainstormSession.Approach::getName).reduce((a, b) -> a + ", " + b).orElse("");
        }

        session.setSelectedApproach(approach);
        session.setCurrentPhase(BrainstormSession.Phase.PRESENT_DESIGN);

        return "Approach selected: " + approach + "\n\nMoving to PRESENT_DESIGN phase.\n" +
            "I'll present the design in sections. Each section requires your approval.";
    }

    private String approveDesign() {
        BrainstormSession session = sessions.get("active");
        if (session == null) return "Error: No active session.";

        switch (session.getCurrentPhase()) {
            case PRESENT_DESIGN:
                if (session.hasMoreDesignSections()) {
                    session.advanceDesignSection();
                    if (session.hasMoreDesignSections()) {
                        BrainstormSession.DesignSection next = session.getCurrentDesignSection();
                        return "Section approved. Next section: " + next.getTitle() + "\n\n" + next.getContent();
                    } else {
                        session.setCurrentPhase(BrainstormSession.Phase.WRITE_DOC);
                        return generateAndSaveDoc(session);
                    }
                }
                session.setCurrentPhase(BrainstormSession.Phase.WRITE_DOC);
                return generateAndSaveDoc(session);

            case WAIT_APPROVAL:
                session.complete();
                return "Design approved! Session completed.\n" +
                    "Design document saved to: " + session.getDesignDocPath() + "\n\n" +
                    "Next step: Use generate_plan to create an implementation plan.";

            default:
                return "Error: Cannot approve in current phase: " + session.getCurrentPhase();
        }
    }

    private String generateAndSaveDoc(BrainstormSession session) {
        try {
            String content = docGenerator.generate(session);
            String fileName = session.getDesignDocFileName();
            var path = docGenerator.saveDoc(content, context.getProject().getBasePath(), fileName);
            session.setDesignDocPath(path.toString());
            session.setCurrentPhase(BrainstormSession.Phase.SELF_REVIEW);

            String reviewResult = selfReview(content);

            session.setCurrentPhase(BrainstormSession.Phase.WAIT_APPROVAL);

            StringBuilder sb = new StringBuilder();
            sb.append("=== Design Document Generated ===\n");
            sb.append("Path: ").append(path).append("\n\n");
            sb.append("=== Self-Review Results ===\n");
            sb.append(reviewResult).append("\n\n");
            sb.append("=== Waiting for your approval ===\n");
            sb.append("Respond with 'approve_design' to approve, or describe changes needed.");

            return sb.toString();
        } catch (Exception e) {
            return "Error generating design doc: " + e.getMessage();
        }
    }

    private String selfReview(String content) {
        StringBuilder issues = new StringBuilder();
        int issueCount = 0;

        String[] placeholders = {"TBD", "TODO", "FIXME", "placeholder", "fill in", "add appropriate"};
        for (String p : placeholders) {
            if (content.toLowerCase().contains(p.toLowerCase())) {
                issues.append("WARNING: Found placeholder '").append(p).append("'\n");
                issueCount++;
            }
        }

        if (content.contains("## Architecture") && content.contains("## Components") &&
            content.contains("## Data Flow")) {
            issues.append("OK: All major design sections present\n");
        } else {
            issues.append("INFO: Some design sections may be missing\n");
        }

        if (issueCount == 0) {
            issues.append("OK: No issues found in self-review\n");
        }

        return issues.toString();
    }

    private String cancelSession() {
        BrainstormSession session = sessions.get("active");
        if (session == null) return "No active session to cancel.";

        session.cancel();
        sessions.remove("active");
        return "Brainstorming session cancelled.";
    }
}
