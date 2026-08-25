package com.example.myplugin.openspec.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Spec {
    private final String title;
    private final String purpose;
    private final List<RequirementBlock> requirements;

    public Spec(String title, String purpose, List<RequirementBlock> requirements) {
        this.title = title;
        this.purpose = purpose;
        this.requirements = requirements != null
            ? Collections.unmodifiableList(requirements)
            : Collections.emptyList();
    }

    public String getTitle() { return title; }
    public String getPurpose() { return purpose; }
    public List<RequirementBlock> getRequirements() { return requirements; }

    public RequirementBlock findRequirement(String name) {
        for (RequirementBlock req : requirements) {
            if (req.getName().equals(name)) {
                return req;
            }
        }
        return null;
    }

    public boolean hasRequirement(String name) {
        return findRequirement(name) != null;
    }

    @Override
    public String toString() {
        return "Spec{title='" + title + "', requirements=" + requirements.size() + "}";
    }
}
