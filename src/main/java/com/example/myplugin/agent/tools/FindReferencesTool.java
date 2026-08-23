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

public class FindReferencesTool implements AgentTool {

    private final AgentContext context;
    private static final Pattern CLASS_DEF = Pattern.compile(
            "(?:public\\s+)?(?:abstract\\s+)?(?:final\\s+)?(?:class|interface|enum|record)\\s+(\\w+)");

    public FindReferencesTool(AgentContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "find_references";
    }

    @Override
    public String description() {
        return "Find all usages/references of a Java class or method in the project. "
                + "Searches file contents for import statements and usages of the class/method name.";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description(description())
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("class_name", "Full or simple class name to find references for")
                        .addStringProperty("method_name", "Method name to find usages of (optional)")
                        .required("class_name")
                        .build())
                .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        String className = arguments.get("class_name").getAsString().trim();
        String methodName = arguments.has("method_name") && !arguments.get("method_name").isJsonNull()
                ? arguments.get("method_name").getAsString().trim() : null;
        Path baseDir = context.getBaseDir();

        String searchTerm = methodName != null ? methodName : className;
        Pattern searchPattern = Pattern.compile("\\b" + Pattern.quote(searchTerm) + "\\b");
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
                         String relPath = baseDir.relativize(file).toString().replace('\\', '/');

                         for (int i = 0; i < lines.length; i++) {
                             if (results.size() >= maxResults) return;
                             String line = lines[i];
                             if (line.trim().startsWith("//")) continue;
                             Matcher m = searchPattern.matcher(line);
                             if (m.find()) {
                                 // Skip the definition line itself
                                 if (methodName == null) {
                                     Matcher defMatcher = CLASS_DEF.matcher(line);
                                     if (defMatcher.find() && defMatcher.group(1).equals(className)) continue;
                                 }
                                 results.add(String.format("%s:%d: %s",
                                         relPath, i + 1, line.trim()));
                             }
                         }
                     } catch (IOException ignored) {}
                 });
        } catch (IOException e) {
            return "Error searching files: " + e.getMessage();
        }

        if (results.isEmpty()) {
            return "No references found for " + (methodName != null ? className + "." + methodName : className);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(results.size()).append(" reference(s) for ");
        sb.append(methodName != null ? className + "." + methodName : className);
        sb.append(":\n\n");
        for (String r : results) {
            sb.append(r).append("\n");
        }
        return sb.toString();
    }
}
