package com.example.myplugin.openspec.tool;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentTool;
import com.example.myplugin.openspec.manager.OpenSpecManager;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.nio.file.Path;

/**
 * Reads the content of an OpenSpec spec file.
 */
public class ReadSpecTool implements AgentTool {

    private final AgentContext context;

    public ReadSpecTool(AgentContext context) {
        this.context = context;
    }

    @Override
    public String name() { return "read_spec"; }

    @Override
    public String description() { return "Read an OpenSpec spec file. Returns the markdown content of the spec."; }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
            .name(name())
            .description(description())
            .parameters(JsonObjectSchema.builder()
                .addProperty("domain", JsonStringSchema.builder()
                    .description("The domain/folder name (e.g. 'cli-archive', 'auth')")
                    .build())
                .build())
            .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        try {
            String domain = arguments.get("domain").getAsString();
            OpenSpecManager manager = new OpenSpecManager(java.nio.file.Path.of(context.getProject().getBasePath()));
            Path specFile = manager.getSpecsDir().resolve(domain).resolve("spec.md");

            if (!java.nio.file.Files.exists(specFile)) {
                return "Spec not found: " + domain;
            }

            return java.nio.file.Files.readString(specFile, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "Error reading spec: " + e.getMessage();
        }
    }
}
