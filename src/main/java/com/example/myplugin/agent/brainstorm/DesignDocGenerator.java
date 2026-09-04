package com.example.myplugin.agent.brainstorm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class DesignDocGenerator {

    public String generate(BrainstormSession session) {
        StringBuilder sb = new StringBuilder();

        sb.append("# ").append(session.getTopic()).append(" Design\n\n");

        sb.append("## Overview\n\n");
        sb.append(generateOverview(session));
        sb.append("\n\n");

        for (BrainstormSession.DesignSection section : session.getDesignSections()) {
            sb.append("## ").append(section.getTitle()).append("\n\n");
            sb.append(section.getContent()).append("\n\n");
        }

        if (!session.getApproaches().isEmpty()) {
            sb.append("## Alternatives Considered\n\n");
            for (BrainstormSession.Approach approach : session.getApproaches()) {
                if (!approach.getName().equals(session.getSelectedApproach())) {
                    sb.append("### ").append(approach.getName()).append("\n\n");
                    sb.append(approach.getDescription()).append("\n\n");
                    if (!approach.getPros().isEmpty()) {
                        sb.append("**Pros:**\n");
                        approach.getPros().forEach(p -> sb.append("- ").append(p).append("\n"));
                        sb.append("\n");
                    }
                    if (!approach.getCons().isEmpty()) {
                        sb.append("**Cons:**\n");
                        approach.getCons().forEach(c -> sb.append("- ").append(c).append("\n"));
                        sb.append("\n");
                    }
                }
            }
        }

        return sb.toString();
    }

    private String generateOverview(BrainstormSession session) {
        StringBuilder sb = new StringBuilder();
        List<String> answers = session.getUserAnswers();
        if (!answers.isEmpty()) {
            for (int i = 0; i < answers.size(); i++) {
                if (i < session.getQuestions().size()) {
                    sb.append("- **").append(session.getQuestions().get(i).getText()).append("**: ");
                    sb.append(answers.get(i)).append("\n");
                }
            }
        }
        if (session.getSelectedApproach() != null) {
            sb.append("\n**Selected approach:** ").append(session.getSelectedApproach()).append("\n");
        }
        return sb.toString();
    }

    public Path saveDoc(String content, String projectPath, String fileName) throws Exception {
        Path docsDir = Paths.get(projectPath, "docs", "specs");
        Files.createDirectories(docsDir);
        Path docPath = docsDir.resolve(fileName);
        Files.writeString(docPath, content, StandardCharsets.UTF_8);
        return docPath;
    }
}
