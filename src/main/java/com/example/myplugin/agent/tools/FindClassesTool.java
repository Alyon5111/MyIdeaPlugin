package com.example.myplugin.agent.tools;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentTool;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class FindClassesTool implements AgentTool {

    private final AgentContext context;
    private static final Pattern CLASS_DEF = Pattern.compile(
            "(?:public\\s+)?(?:abstract\\s+)?(?:final\\s+)?(?:class|interface|enum|record)\\s+(\\w+)");

    public FindClassesTool(AgentContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "find_classes";
    }

    @Override
    public String description() {
        return "Search for Java classes by name (partial or full). "
                + "Returns matching class names, their file locations, and package. "
                + "Searches file contents for class/interface/enum/record definitions.";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description(description())
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("name", "Class name or partial name to search for")
                        .required("name")
                        .build())
                .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        String name = arguments.get("name").getAsString().trim();
        Path baseDir = context.getBaseDir();
        List<String> results = new ArrayList<>();
        int maxResults = 50;

        try (Stream<Path> files = Files.walk(baseDir)) {
            files.filter(p -> p.toString().endsWith(".java"))
                 .filter(p -> !p.toString().contains("build"))
                 .forEach(file -> {
                     if (results.size() >= maxResults) return;
                     try {
                         String content = Files.readString(file);
                         String[] lines = content.split("\n");
                         String packageName = "";
                         for (String line : lines) {
                             if (line.startsWith("package ")) {
                                 packageName = line.replace("package ", "").replace(";", "").trim();
                             }
                             Matcher m = CLASS_DEF.matcher(line);
                             if (m.find()) {
                                 String className = m.group(1);
                                 if (className.toLowerCase().contains(name.toLowerCase())) {
                                     String relPath = baseDir.relativize(file).toString().replace('\\', '/');
                                     results.add(String.format("%s  [package: %s]  [file: %s]",
                                             className, packageName.isEmpty() ? "(default)" : packageName, relPath));
                                     if (results.size() >= maxResults) return;
                                 }
                             }
                         }
                     } catch (IOException ignored) {}
                 });
        } catch (IOException e) {
            return "Error searching files: " + e.getMessage();
        }

        if (results.isEmpty()) {
            return "No classes found matching: " + name;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(results.size()).append(" class(es):\n\n");
        for (String r : results) {
            sb.append(r).append("\n");
        }
        return sb.toString();
    }
}
