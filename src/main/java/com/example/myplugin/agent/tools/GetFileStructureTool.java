package com.example.myplugin.agent.tools;

import com.example.myplugin.agent.AgentContext;
import com.example.myplugin.agent.AgentTool;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GetFileStructureTool implements AgentTool {

    private static final Pattern TYPE_DEF = Pattern.compile(
            "(?<![.\\w])(class|interface|enum|record)\\s+(\\w+)\\s*(\\([^)]*\\))?"
                    + "(\\s+extends\\s+[\\w.]+)?(\\s+implements\\s+[\\w.,\\s]+?)?\\s*\\{?\\s*$");
    private static final Pattern FIELD_DEF = Pattern.compile(
            "^((?:[A-Za-z_$][\\w$.]*)|(?:[A-Za-z_$][\\w$.]*<[^=]*?>))(?:\\[\\])*\\s+(\\w+)\\s*(?:=[^=].*)?;$");
    private static final Set<String> CONTROL_KEYWORDS = Set.of(
            "if", "for", "while", "switch", "catch", "do", "else", "try",
            "return", "throw", "new", "case", "finally", "assert", "synchronized", "this", "super");
    private static final int MAX_ENTRIES = 400;

    private final AgentContext context;

    public GetFileStructureTool(AgentContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "get_file_structure";
    }

    @Override
    public String description() {
        return "Extract a structural outline of a source file without reading its full content: "
                + "package declaration, type declarations (classes/interfaces/enums/records with "
                + "extends/implements), method signatures, and fields with nesting indicated by indentation. "
                + "Works best for Java-like files. Much cheaper than read_file for orientation.";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(name())
                .description(description())
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("path", "File path relative to project root")
                        .required("path")
                        .build())
                .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        if (!arguments.has("path") || arguments.get("path").isJsonNull()) {
            return "Error: missing required parameter 'path'";
        }
        String path = arguments.get("path").getAsString().trim();
        Path filePath = context.getBaseDir().resolve(path).normalize();

        if (!filePath.startsWith(context.getBaseDir())) {
            return "Error: path escapes project directory";
        }
        if (!Files.exists(filePath)) {
            return "Error: file not found: " + path;
        }

        try {
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            return outline(content, path);
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    private String outline(String content, String path) {
        String packageName = "";
        Matcher packageMatcher = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE)
                .matcher(content);
        if (packageMatcher.find()) {
            packageName = packageMatcher.group(1);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Structure of ").append(path).append("\n");
        if (!packageName.isEmpty()) {
            sb.append("package ").append(packageName).append("\n");
        }
        sb.append("\n");

        String[] lines = content.split("\n", -1);
        int depth = 0;
        int entries = 0;
        int types = 0;
        int methods = 0;
        int fields = 0;
        boolean inBlockComment = false;

        for (int i = 0; i < lines.length; i++) {
            String work = lines[i];

            if (inBlockComment) {
                int endIdx = work.indexOf("*/");
                if (endIdx < 0) continue;
                work = work.substring(endIdx + 2);
                inBlockComment = false;
            }
            int blockStart = work.indexOf("/*");
            int lineComment = work.indexOf("//");
            if (blockStart >= 0 && (lineComment < 0 || blockStart < lineComment)) {
                int endIdx = work.indexOf("*/", blockStart + 2);
                if (endIdx >= 0) {
                    work = work.substring(0, blockStart) + work.substring(endIdx + 2);
                } else {
                    work = work.substring(0, blockStart);
                    inBlockComment = true;
                }
            }
            lineComment = work.indexOf("//");
            if (lineComment >= 0) {
                work = work.substring(0, lineComment);
            }

            String trimmed = work.trim();
            int opens = count(work, '{');
            int closes = count(work, '}');

            if (!trimmed.isEmpty() && entries < MAX_ENTRIES && sb.length() < 40000) {
                String indent = "  ".repeat(Math.max(0, depth));
                String entry = classify(trimmed, indent, i + 1);
                if (entry != null) {
                    sb.append(entry).append('\n');
                    entries++;
                    String body = entry.substring(indent.length());
                    if (body.startsWith("type ")) types++;
                    else if (body.startsWith("method ")) methods++;
                    else if (body.startsWith("field ")) fields++;
                }
            }

            depth = Math.max(0, depth + opens - closes);
        }

        sb.append('\n')
                .append(types).append(" type(s), ")
                .append(methods).append(" method(s)/constructor(s), ")
                .append(fields).append(" field(s)")
                .append(" [").append(lines.length).append(" total lines]\n");

        if (entries >= MAX_ENTRIES) {
            sb.append("... (truncated at ").append(MAX_ENTRIES).append(" entries)\n");
        }
        return sb.toString();
    }

    private String classify(String trimmed, String indent, int lineNumber) {
        if (trimmed.startsWith("*") || trimmed.startsWith("/*")
                || trimmed.startsWith("@") || trimmed.equals("{") || trimmed.equals("}")) {
            return null;
        }

        Matcher tm = TYPE_DEF.matcher(trimmed);
        if (tm.find()) {
            StringBuilder typeDesc = new StringBuilder();
            typeDesc.append(indent)
                    .append("type ").append(tm.group(1)).append(' ').append(tm.group(2));
            if (tm.group(3) != null) {
                typeDesc.append(tm.group(3));
            }
            if (tm.group(4) != null) {
                typeDesc.append(tm.group(4).trim());
            }
            if (tm.group(5) != null) {
                typeDesc.append(" implements ")
                        .append(trimBraces(tm.group(5).replaceFirst("(?i)^implements\\s+", "").trim()));
            }
            typeDesc.append(String.format("   [%d]", lineNumber));
            return typeDesc.toString();
        }

        if (trimmed.endsWith(";") && !trimmed.startsWith("import") && !trimmed.startsWith("package")) {
            Matcher fm = FIELD_DEF.matcher(trimmed);
            if (fm.matches()) {
                return indent + "field " + fm.group(2) + " : " + fm.group(1)
                        + String.format("   [%d]", lineNumber);
            }
            return null;
        }

        if (trimmed.contains("(") && !trimmed.contains("->")) {
            String prefix = trimmed.substring(0, trimmed.indexOf('(')).trim();
            String[] tokens = prefix.split("\\s+");
            if (tokens.length >= 2 && !CONTROL_KEYWORDS.contains(tokens[0].toLowerCase())) {
                String sig = trimmed.endsWith("{")
                        ? trimmed.substring(0, trimmed.length() - 1).trim() : trimmed;
                return indent + "method " + sig + String.format("   [%d]", lineNumber);
            }
        }
        return null;
    }

    private String trimBraces(String s) {
        String result = s.trim();
        while (result.endsWith("{") || result.endsWith("}")) {
            result = result.substring(0, result.length() - 1).trim();
        }
        return result;
    }

    private int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) n++;
        }
        return n;
    }
}
