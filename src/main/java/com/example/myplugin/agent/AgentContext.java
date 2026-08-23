package com.example.myplugin.agent;

import com.intellij.openapi.project.Project;

import java.nio.file.Path;

public class AgentContext {

    private final Project project;
    private final Path baseDir;

    public AgentContext(Project project) {
        this.project = project;
        this.baseDir = Path.of(project.getBasePath());
    }

    public Project getProject() {
        return project;
    }

    public Path getBaseDir() {
        return baseDir;
    }
}
