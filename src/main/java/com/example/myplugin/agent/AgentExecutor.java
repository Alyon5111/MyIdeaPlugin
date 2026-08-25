package com.example.myplugin.agent;

import com.example.myplugin.agent.tools.*;
import com.example.myplugin.openspec.tool.*;
import com.example.myplugin.model.Conversation;
import com.example.myplugin.settings.PluginStateService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.project.Project;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AgentExecutor {

    private static final int MAX_ITERATIONS = 15;
    private static final int MAX_TOTAL_TOOL_CALLS = 8;
    private static final Gson GSON = new Gson();
    private static final SystemMessage BASE_SYSTEM_MESSAGE = SystemMessage.from(
            "You are an expert coding agent. Use tools to explore the codebase, then provide a clear final answer.\n\n" +
            "RULES:\n" +
            "1. Use tools to gather information, but do NOT call the same tool repeatedly on the same file.\n" +
            "2. After 2-3 tool calls, you MUST stop calling tools and provide your final answer as text.\n" +
            "3. Do NOT output tool_call tags in your final answer.\n" +
            "4. Keep file paths relative to the project root.\n" +
            "OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + ".");

    private static final Map<String, String> ROLE_PROMPTS;
    static {
        Map<String, String> rp = new HashMap<>();
        rp.put("review",
                "You are a CODE REVIEWER. Focus on finding bugs, code smells, security issues, and improvements. " +
                "Check for null pointer risks, resource leaks, exception handling, naming conventions, and design patterns. " +
                "Rate severity: CRITICAL / WARNING / SUGGESTION. Be specific with line numbers.");
        rp.put("architect",
                "You are a SOFTWARE ARCHITECT. Focus on system design, module dependencies, coupling/cohesion, " +
                "design patterns, scalability, and maintainability. " +
                "Analyze the overall structure, identify architectural anti-patterns, and suggest improvements.");
        rp.put("test",
                "You are a TEST ENGINEER. Focus on test coverage, test quality, edge cases, and testability. " +
                "Identify untested code paths, suggest test cases, check for proper mocking, " +
                "and evaluate boundary conditions. Report coverage gaps.");
        ROLE_PROMPTS = Collections.unmodifiableMap(rp);
    }

    private static final Map<String, Set<String>> ROLE_KEYWORDS;
    static {
        Map<String, Set<String>> rk = new HashMap<>();
        rk.put("review", Set.of("review", "audit", "check code", "code review", "cr", "pr review", "pull request"));
        rk.put("architect", Set.of("architecture", "design", "structure", "dependency", "dependencies", "coupling", "refactor"));
        rk.put("test", Set.of("test coverage", "unit test", "integration test", "test case", "mock", "assertion", "coverage"));
        ROLE_KEYWORDS = Collections.unmodifiableMap(rk);
    }

    private String detectRole(String userMessage) {
        String lower = userMessage.toLowerCase();
        for (Map.Entry<String, Set<String>> entry : ROLE_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return "dev";
    }

    private SystemMessage buildSystemMessage(String role, String extraTools) {
        String rolePrompt = ROLE_PROMPTS.getOrDefault(role,
                "You are a DEVELOPER. Write clean, correct code. " +
                "Follow existing code conventions and patterns in the project.");
        return SystemMessage.from(BASE_SYSTEM_MESSAGE.text() + "\n\n" + rolePrompt + extraTools);
    }

    public interface AgentEvent {
        default void onThinking(String text) {}
        default void onToolCall(String toolName, String arguments) {}
        default void onToolResult(String toolName, String result) {}
        default void onAnswer(String text) {}
        default void onError(String error) {}
    }

    private final List<AgentTool> tools;
    private final List<ToolSpecification> toolSpecs;
    private final Map<String, AgentTool> toolMap;
    private ChatModel model;
    private volatile boolean cancelled = false;
    private String lastAnswer;

    public AgentExecutor(Project project) {
        AgentContext ctx = new AgentContext(project);

        tools = List.of(
                new ReadFileTool(ctx),
                new WriteFileTool(ctx),
                new EditFileTool(ctx),
                new ListFilesTool(ctx),
                new SearchCodeTool(ctx),
                new ExecuteCommandTool(ctx),
                new FindClassesTool(ctx),
                new FindReferencesTool(ctx),
                new GetDiagnosticsTool(ctx),
                new RunTestsTool(ctx),
                new ReformatCodeTool(ctx),
                new GetFileStructureTool(ctx),
                new GotoDefinitionTool(ctx),
                new GetProjectStructureTool(ctx),
                new GitDiffTool(ctx),
                new GitLogTool(ctx),
                new RunGradleTaskTool(ctx),
                new CheckInspectionsTool(ctx),
                new ReadSpecTool(ctx),
                new ListSpecsTool(ctx),
                new ListChangesTool(ctx),
                new CreateChangeTool(ctx),
                new ArchiveChangeTool(ctx),
                new SDDStatusTool(ctx)
        );

        toolSpecs = new ArrayList<>();
        toolMap = new HashMap<>();
        for (AgentTool tool : tools) {
            toolSpecs.add(tool.specification());
            toolMap.put(tool.name(), tool);
        }
    }

    private ChatModel createModel(String modelName) {
        PluginStateService state = PluginStateService.getInstance();
        String url = state.getLlamaCppUrl();
        if (url == null || url.isEmpty()) {
            url = "http://localhost:8080";
        }
        if (!url.endsWith("/v1")) {
            url = url.replaceAll("/+$", "") + "/v1";
        }
        return OpenAiChatModel.builder()
                .baseUrl(url)
                .apiKey(state.getApiKey() != null ? state.getApiKey() : "no-key")
                .modelName(modelName)
                .temperature(0.3)
                .topP(0.9)
                .maxRetries(1)
                .timeout(Duration.ofSeconds(300))
                .build();
    }

    private static final Set<String> CORE_TOOLS = Set.of(
            "read_file", "list_files", "search_code", "execute_command"
    );

    private static final Map<String, Set<String>> TOOL_KEYWORDS;
    static {
        Map<String, Set<String>> m = new HashMap<>();
        m.put("find_classes", Set.of("class", "interface", "enum", "record", "find class", "classes"));
        m.put("find_references", Set.of("reference", "usage", "usages", "who uses", "who calls"));
        m.put("get_file_structure", Set.of("structure", "outline", "methods", "fields", "skeleton"));
        m.put("goto_definition", Set.of("definition", "define", "where is defined", "jump to"));
        m.put("get_diagnostics", Set.of("diagnostic", "error", "warning", "compile error", "compiler"));
        m.put("check_inspections", Set.of("inspection", "inspect", "lint", "code review"));
        m.put("run_tests", Set.of("test", "junit", "run test", "tests"));
        m.put("reformat_code", Set.of("format", "reformat", "code style", "pretty print"));
        m.put("run_gradle_task", Set.of("gradle", "maven", "build", "dependenc", "compile project"));
        m.put("git_diff", Set.of("diff", "change", "changed", "what changed", "modified"));
        m.put("git_log", Set.of("log", "commit", "history", "recent commits"));
        m.put("get_project_structure", Set.of("project structure", "module", "modules", "source root"));
        m.put("write_file", Set.of("write", "create file", "new file", "create"));
        m.put("edit_file", Set.of("edit", "modify", "change file", "update file", "replace"));
        m.put("read_spec", Set.of("spec", "specification", "read spec", "requirement spec"));
        m.put("list_specs", Set.of("list spec", "all spec", "specs", "show spec"));
        m.put("list_changes", Set.of("change", "changes", "active change", "list change"));
        m.put("create_change", Set.of("new change", "create change", "start change", "propose"));
        m.put("archive_change", Set.of("archive", "finish change", "complete change", "merge spec"));
        m.put("sdd_status", Set.of("sdd", "status", "openspec status", "sdd status"));
        TOOL_KEYWORDS = Collections.unmodifiableMap(m);
    }

    private List<AgentTool> selectToolsForMessage(String userMessage) {
        String lower = userMessage.toLowerCase();
        Set<String> selectedNames = new HashSet<>(CORE_TOOLS);

        for (Map.Entry<String, Set<String>> entry : TOOL_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword)) {
                    selectedNames.add(entry.getKey());
                    break;
                }
            }
        }

        if (selectedNames.size() <= CORE_TOOLS.size() + 2) {
            selectedNames.add("write_file");
            selectedNames.add("edit_file");
        }

        return selectedNames.stream()
                .map(toolMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public String execute(List<Conversation.ChatMessage> history, String modelName, AgentEvent event) {
        cancelled = false;
        lastAnswer = null;
        model = createModel(modelName);

        String lastUserMsg = "";
        for (Conversation.ChatMessage msg : history) {
            if (msg.getRole() == Conversation.Role.USER) {
                lastUserMsg = msg.getContent();
            }
        }

        String role = detectRole(lastUserMsg);
        List<AgentTool> selectedTools = selectToolsForMessage(lastUserMsg);
        List<ToolSpecification> activeToolSpecs = selectedTools.stream()
                .map(AgentTool::specification)
                .collect(Collectors.toList());
        String toolList = selectedTools.stream().map(AgentTool::name).collect(Collectors.joining(", "));
        SystemMessage systemMsg = buildSystemMessage(role, "\n\nAvailable tools: " + toolList);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(systemMsg);

        for (Conversation.ChatMessage msg : history) {
            if (msg.getRole() == Conversation.Role.USER) {
                messages.add(UserMessage.from(msg.getContent()));
            } else if (msg.getRole() == Conversation.Role.ASSISTANT) {
                messages.add(AiMessage.from(msg.getContent()));
            }
        }

        event.onThinking("Starting agent with model: " + modelName);
        event.onThinking("Role: " + role);
        event.onThinking("Selected tools (" + selectedTools.size() + "): " + toolList);
        event.onThinking("---");

        java.util.Map<String, Integer> callCounts = new HashMap<>();
        int totalToolCalls = 0;

        for (int i = 0; i < MAX_ITERATIONS && !cancelled; i++) {
            try {
                event.onThinking("Iteration " + (i + 1) + "/" + MAX_ITERATIONS);

                ChatRequest request = ChatRequest.builder()
                        .messages(messages)
                        .toolSpecifications(activeToolSpecs)
                        .build();

                ChatResponse response = model.chat(request);
                AiMessage aiMessage = response.aiMessage();

                if (aiMessage.hasToolExecutionRequests()) {
                    event.onThinking("Model requested " + aiMessage.toolExecutionRequests().size() + " tool call(s)");
                    messages.add(aiMessage);

                    for (ToolExecutionRequest toolReq : aiMessage.toolExecutionRequests()) {
                        if (cancelled) break;

                        String toolName = toolReq.name();
                        String args = toolReq.arguments();
                        totalToolCalls++;

                        if (totalToolCalls > MAX_TOTAL_TOOL_CALLS) {
                            event.onThinking("Total tool call limit reached (" + MAX_TOTAL_TOOL_CALLS + "), forcing final answer");
                            messages.add(UserMessage.from(
                                    "STOP. You have used all your tool calls. " +
                                    "Provide your final answer now based on the information you have gathered."));
                            break;
                        }

                        String callKey = toolName + ":" + args;
                        int count = callCounts.getOrDefault(callKey, 0) + 1;
                        callCounts.put(callKey, count);

                        if (count >= 2) {
                            event.onThinking("Repeated tool call detected, forcing final answer");
                            messages.add(UserMessage.from(
                                    "STOP. You have already called this exact same tool with the same arguments. " +
                                    "Do NOT call any more tools. Provide your final answer now as plain text."));
                            break;
                        }

                        event.onToolCall(toolName, args);

                        AgentTool tool = toolMap.get(toolName);
                        String result;
                        if (tool == null) {
                            result = "Error: unknown tool: " + toolName;
                        } else {
                            try {
                                JsonObject argsJson = JsonParser.parseString(args).getAsJsonObject();
                                result = tool.execute(argsJson);
                            } catch (Exception e) {
                                result = "Error executing tool: " + e.getMessage();
                            }
                        }

                        event.onToolResult(toolName, result);
                        messages.add(ToolExecutionResultMessage.from(toolReq, result));
                    }
                } else {
                    String text = aiMessage.text();
                    if (text != null && !text.isEmpty()) {
                        event.onAnswer(text);
                    }
                    lastAnswer = text;
                    return text;
                }
            } catch (Exception e) {
                String error = "Agent error: " + e.getMessage();
                if (e instanceof java.net.ConnectException) {
                    String url = PluginStateService.getInstance().getLlamaCppUrl();
                    error = "Cannot connect to LLM server at " + url + "\n"
                            + "Make sure your llama.cpp server is running.\n"
                            + "Original error: " + e.getMessage();
                }
                event.onError(error);
                lastAnswer = error;
                return error;
            }
        }

        if (cancelled) {
            lastAnswer = "[stopped]";
            event.onAnswer("\n[Agent stopped by user]");
            return "[stopped]";
        }

        String fallback = "Agent reached maximum iterations without completing.";
        lastAnswer = fallback;
        event.onAnswer(fallback);
        return fallback;
    }

    public void cancel() {
        cancelled = true;
    }

    public String getLastAnswer() {
        return lastAnswer;
    }

    public List<String> getToolNames() {
        return tools.stream().map(AgentTool::name).toList();
    }
}
