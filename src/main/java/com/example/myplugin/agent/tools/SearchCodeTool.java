package com.example.myplugin.agent.tools;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentTool;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class SearchCodeTool implements AgentTool {

    private final AgentContext context;

    public SearchCodeTool(AgentContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "search_code";
    }

    @Override
    public String description() {
        return "Search for a regex pattern across project source files. "
                + "Returns matching lines with file path and line number. "
                + "Optionally filter by file extension (e.g. \"*.java\").";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description(description())
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("pattern", "Java regex pattern to search for")
                        .addStringProperty("file_pattern", "Glob pattern to filter files, e.g. \"*.java\" (optional)")
                        .required("pattern")
                        .build())
                .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        String patternStr = arguments.get("pattern").getAsString();
        String filePattern = arguments.has("file_pattern") && !arguments.get("file_pattern").isJsonNull()
                ? arguments.get("file_pattern").getAsString() : null;

        Pattern pattern;
        try {
            pattern = Pattern.compile(patternStr, Pattern.MULTILINE);
        } catch (PatternSyntaxException e) {
            return "Error: invalid regex pattern: " + e.getMessage();
        }

        Path baseDir = context.getBaseDir();
        List<String> results = new ArrayList<>();
        int maxResults = 100;

        try {
            ProcessBuilder pb = new ProcessBuilder();
            List<String> cmd = new ArrayList<>();
            cmd.add("grep");
            cmd.add("-rn");
            cmd.add("--include=" + (filePattern != null ? filePattern : "*"));
            cmd.add(patternStr);
            cmd.add(".");

            pb.directory(baseDir.toFile());
            pb.redirectErrorStream(true);
            pb.command(cmd);

            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null && results.size() < maxResults) {
                    if (line.contains("Binary file") || line.isEmpty()) continue;
                    results.add(line);
                }
            }
            process.waitFor();
        } catch (Exception e) {
            return searchWithJava(baseDir, pattern, filePattern, maxResults);
        }

        if (results.isEmpty()) {
            return "No matches found for pattern: " + patternStr;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(results.size()).append(" match(es):\n\n");
        for (String line : results) {
            sb.append(line).append("\n");
        }
        if (results.size() >= maxResults) {
            sb.append("\n... (truncated at ").append(maxResults).append(" results)");
        }
        return sb.toString();
    }

    private String searchWithJava(Path baseDir, Pattern pattern, String filePattern, int maxResults) {
        List<String> results = new ArrayList<>();

        try (var walk = Files.walk(baseDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        if (name.startsWith(".") || name.equals("node_modules")
                                || name.equals("build") || name.equals("target")
                                || name.equals("__pycache__") || name.equals(".git")) {
                            return false;
                        }
                        if (filePattern != null) {
                            String glob = filePattern.replace("*.", "\\.").replace("*", ".*");
                            return name.matches(glob);
                        }
                        return true;
                    })
                    .forEach(p -> {
                        if (results.size() >= maxResults) return;
                        try {
                            List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                            String relPath = baseDir.relativize(p).toString();
                            for (int i = 0; i < lines.size(); i++) {
                                if (results.size() >= maxResults) break;
                                if (pattern.matcher(lines.get(i)).find()) {
                                    results.add(relPath + ":" + (i + 1) + ": " + lines.get(i).trim());
                                }
                            }
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException e) {
            return "Error searching files: " + e.getMessage();
        }

        if (results.isEmpty()) {
            return "No matches found for pattern: " + pattern.pattern();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(results.size()).append(" match(es):\n\n");
        for (String line : results) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }
}
