package com.example.myplugin.agent;

import com.example.myplugin.agent.tools.*;
import com.example.myplugin.agent.brainstorm.BrainstormTool;
import com.example.myplugin.agent.plan.GeneratePlanTool;
import com.example.myplugin.agent.plan.ExportPlanTool;
import com.example.myplugin.agent.tdd.TddEnforcerTool;
import com.example.myplugin.agent.debugging.SystematicDebuggingTool;
import com.example.myplugin.agent.verification.VerificationGateTool;
import com.example.myplugin.agent.review.CodeReviewTool;
import com.example.myplugin.agent.plans.ExecutingPlansTool;
import com.example.myplugin.agent.subagent.SubagentDrivenDevelopmentTool;
import com.example.myplugin.agent.git.GitWorkflowTool;
import com.example.myplugin.agent.branch.FinishingBranchTool;
import com.example.myplugin.agent.memory.AgentMemoryService;
import com.example.myplugin.agent.memory.systemmemory.ProjectKnowledgeTool;
import com.example.myplugin.agent.memory.semanticmemory.ConversationMemoryTool;
import com.example.myplugin.agent.memory.toolmemory.MemoryStatusTool;
import com.example.myplugin.agent.memory.toolmemory.ToolMemoryManager;
import com.example.myplugin.agent.pipeline.PipelineService;
import com.example.myplugin.agent.pipeline.SDDPipelineTool;
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
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AgentExecutor {

    private static final Gson GSON = new Gson();

    // Truncate individual tool results before they enter the context window (~1k tokens).
    private static final int TOOL_RESULT_MAX_CHARS = 4000;

    // Weak tool-following models (qwen-3-27b) will explore forever instead of writing code.
    // In TDD runs, treat these as "exploration" calls; after the budget runs out the tools are
    // removed from the active spec set so the model physically cannot keep reading files.
    private static final java.util.Set<String> TDD_EXPLORATION_TOOLS =
            java.util.Set.of("list_files", "read_file", "search_code", "execute_command");
    private static final int TDD_EXPLORATION_BUDGET = 10;
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
        String tail = systemPromptTailOverride != null ? "\n\n" + systemPromptTailOverride : "";
        systemPromptTailOverride = null;
        return SystemMessage.from(BASE_SYSTEM_MESSAGE.text() + "\n\n" + rolePrompt + extraTools + tail);
    }

    public interface AgentEvent {
        default void onThinking(String text) {}
        default void onToolCall(String toolName, String arguments) {}
        default void onToolResult(String toolName, String result) {}
        default void onAnswer(String text) {}
        default void onError(String error) {}
        default void onContextTrim(int dropped, int kept, int maxContextMessages) {}
        default void onAnswerFragment(String text) {}
    }

    private final List<AgentTool> tools;
    private final List<ToolSpecification> toolSpecs;
    private final Map<String, AgentTool> toolMap;
    private ChatModel model;
    private StreamingChatModel streamingModel;
    private volatile boolean cancelled = false;
    private volatile CompletableFuture<ChatResponse> inFlightCall;
    private String lastAnswer;
    private final AgentMemoryService memoryService;
    private String strongForcedTool;
    private int toolCallLimitOverride = -1;
    private int maxIterationsOverride = -1;
    private String roleOverride;
    private String systemPromptTailOverride;
    private boolean suppressMemoryInjectionOverride = false;

    /** Overrides the tool-call limit for the next execute() call (e.g. long TDD chains). -1 = use settings default. */
    public void setToolCallLimitOverride(int limit) {
        this.toolCallLimitOverride = limit;
    }

    /** Overrides the iteration limit for the next execute() call. -1 = use settings default. */
    public void setMaxIterationsOverride(int iterations) {
        this.maxIterationsOverride = iterations;
    }

    /** Forces the role used for the next execute() call. Null = auto-detect. */
    public void setRoleOverride(String role) {
        this.roleOverride = role;
    }

    /** Appends extra system-prompt instructions for the next execute() call. Null = none. */
    public void setSystemPromptTail(String tail) {
        this.systemPromptTailOverride = tail;
    }

    /** Suppresses the semantic-memory injection for the next execute() call (clean-room TDD runs). */
    public void setSuppressMemoryInjection(boolean suppress) {
        this.suppressMemoryInjectionOverride = suppress;
    }

    /** Directly runs one of this executor's tools, bypassing the model. Returns the tool output. */
    public String runTool(String toolName, String jsonArgs) {
        AgentTool tool = toolMap.get(toolName);
        if (tool == null) return "Unknown tool: " + toolName;
        try {
            return tool.execute(JsonParser.parseString(jsonArgs).getAsJsonObject());
        } catch (Exception e) {
            return "Error executing tool: " + e.getMessage();
        }
    }

    public AgentExecutor(Project project) {
        AgentContext ctx = new AgentContext(project);
        AgentMemoryService memoryService = new AgentMemoryService(project);
        ToolMemoryManager toolMemory = new ToolMemoryManager(memoryService);
        this.memoryService = memoryService;
        PipelineService pipelineService = new PipelineService(project);
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
                new ReadChangeTool(ctx),
                new ListSpecsTool(ctx),
                new ListChangesTool(ctx),
                new CreateChangeTool(ctx),
                new ArchiveChangeTool(ctx),
                new SDDStatusTool(ctx),
                new BrainstormTool(ctx),
                new GeneratePlanTool(ctx),
                new ExportPlanTool(ctx),
                new TddEnforcerTool(ctx, pipelineService),
                new SystematicDebuggingTool(),
                new VerificationGateTool(),
                new CodeReviewTool(),
                new ExecutingPlansTool(),
                new SubagentDrivenDevelopmentTool(),
                new GitWorkflowTool(),
                new FinishingBranchTool(),
                new ProjectKnowledgeTool(memoryService),
                new ConversationMemoryTool(memoryService),
                new MemoryStatusTool(memoryService, toolMemory),
                new SDDPipelineTool(ctx, pipelineService)
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
                .temperature(state.getTemperature())
                .topP(state.getTopP())
                .maxRetries(state.getMaxRetries())
                .timeout(Duration.ofSeconds(PluginStateService.getInstance().getTimeout()))
                .build();
    }

    private StreamingChatModel createStreamingModel(String modelName) {
        PluginStateService state = PluginStateService.getInstance();
        String url = state.getLlamaCppUrl();
        if (url == null || url.isEmpty()) {
            url = "http://localhost:8080";
        }
        if (!url.endsWith("/v1")) {
            url = url.replaceAll("/+$", "") + "/v1";
        }
        return OpenAiStreamingChatModel.builder()
                .baseUrl(url)
                .apiKey(state.getApiKey() != null ? state.getApiKey() : "no-key")
                .modelName(modelName)
                .temperature(state.getTemperature())
                .topP(state.getTopP())
                .timeout(Duration.ofSeconds(PluginStateService.getInstance().getTimeout()))
                .build();
    }

    /**
     * Bridges the synchronous tool loop to the streaming model. Each iteration blocks until the
     * full response arrives, streaming partial text prose out via onAnswerFragment so the UI can
     * render answers incrementally while tool calls still flow through the same message loop.
     */
    private ChatResponse callModel(ChatRequest request, AgentEvent event, int timeoutSeconds) throws Exception {
        if (streamingModel == null) {
            return model.chat(request);
        }
        CompletableFuture<ChatResponse>[] ref = new CompletableFuture[]{new CompletableFuture<>()};
        inFlightCall = ref[0];
        try {
            streamingModel.chat(request, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partial) {
                    if (!cancelled && partial != null && !partial.isBlank()) {
                        event.onAnswerFragment(partial);
                    }
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    ref[0].complete(response);
                }

                @Override
                public void onError(Throwable error) {
                    ref[0].completeExceptionally(error);
                }
            });
            try {
                return ref[0].get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new RuntimeException("Agent model request timed out after " + timeoutSeconds + "s");
            }
        } finally {
            inFlightCall = null;
        }
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
        m.put("read_change", Set.of("read change", "proposal", "tasks", "change plan"));
        m.put("list_specs", Set.of("list spec", "all spec", "specs", "show spec"));
        m.put("list_changes", Set.of("list change", "active change", "list changes", "openspec"));
        m.put("create_change", Set.of("new change", "create change", "start change", "propose"));
        m.put("archive_change", Set.of("archive", "finish change", "complete change", "merge spec"));
        m.put("sdd_status", Set.of("sdd", "sdd status", "openspec status"));
        m.put("brainstorm", Set.of("brainstorm", "design", "plan feature", "architect", "design decision", "think about"));
        m.put("generate_plan", Set.of("generate plan", "create plan", "implementation plan", "write plan", "task list", "step by step"));
        m.put("tdd_enforcer", Set.of("tdd", "test driven", "red green", "write test first", "failing test", "tdd workflow"));
        m.put("systematic_debugging", Set.of("debug", "debugging", "bug", "root cause", "investigate", "fix bug", "systematic"));
        m.put("verification_gate", Set.of("verify", "verification", "check", "validate", "evidence", "confirm", "gate"));
        m.put("code_review", Set.of("review code", "code review", "cr", "audit", "check code", "yagni", "performative"));
        m.put("executing_plans", Set.of("execute plan", "run plan", "implement plan", "task list", "plan execution"));
        m.put("subagent_driven_development", Set.of("subagent", "sub-agent", "dispatch agent", "parallel task", "two-stage review"));
        m.put("git_workflow", Set.of("worktree", "git worktree", "branch management", "git branch", "create branch"));
        m.put("finishing_branch", Set.of("finish branch", "complete branch", "merge branch", "create pr", "pull request", "discard work"));
        m.put("project_knowledge", Set.of("project knowledge", "tech stack", "convention", "naming convention", "remember", "recall", "remembered", "previously"));
        m.put("conversation_memory", Set.of("remember", "recall", "previous session", "remembered", "what did we", "search memory", "semantic"));
        m.put("memory_status", Set.of("memory status", "memory summary", "memory list", "memories", "memory stats"));
        m.put("sdd_pipeline", Set.of("spec pipeline", "sdd pipeline", "driven development", "pipeline", "advance tdd", "spectotdd", "spec to tdd", "next stage"));
        TOOL_KEYWORDS = Collections.unmodifiableMap(m);
    }

    /**
     * Strong directive phrases that must dispatch to exactly one tool. Matching is substring-based
     * on the lowercased user message, so include CJK fragments for Chinese instructions too.
     * When any of these is present, the candidate set is narrowed to exactly the owning tool so a
     * weak model (qwen-3-27b) cannot drift toward core/exploration tools.
     */
    private static final Map<String, Set<String>> STRONG_TOOL_KEYWORDS;
    static {
        Map<String, Set<String>> m = new HashMap<>();
        m.put("generate_plan", Set.of("generate plan", "create plan", "write plan", "implementation plan", "plan document", "\u751f\u6210\u8ba1\u5212"));
        m.put("export_plan", Set.of("export plan", "\u5bfc\u51fa\u8ba1\u5212", "\u5bfc\u51fa\u8fd9\u4e2a\u8ba1\u5212", "export_plan"));
        m.put("tdd_enforcer", Set.of("tdd start", "tdd cycle", "tdd start_cycle", "tdd write_test", "tdd verify_test", "tdd write_code", "tdd verify_code", "tdd mark_refactor", "tdd get_status", "tdd cancel", "tdd link_pipeline", "red green refactor", "start_cycle", "link_pipeline", "\u542f\u52a8 tdd", "tdd \u5faa\u73af", "\u5f00\u59cb tdd", "\u5173\u8054\u5230\u6d41\u6c34\u7ebf"));
        m.put("sdd_pipeline", Set.of("pipeline start", "pipeline add_step", "pipeline advance_plan", "pipeline start_tdd", "pipeline complete_tdd", "pipeline link_change", "pipeline complete", "pipeline status", "pipeline list", "pipeline next_step"));
        m.put("create_change", Set.of("create_change", "create change", "new change", "start a change", "openspec change", "\u521b\u5efa\u53d8\u66f4"));
        m.put("archive_change", Set.of("archive change", "complete change", "\u5f52\u6863\u53d8\u66f4"));
        STRONG_TOOL_KEYWORDS = Collections.unmodifiableMap(m);
    }

    private List<AgentTool> selectToolsForMessage(String userMessage) {
        String lower = userMessage.toLowerCase();
        Set<String> selectedNames = new HashSet<>();

        // Strong directive phrases force exactly the owning tool into the candidate set. For weak
        // tool-following models (qwen-3-27b) even CORE_TOOLS are excluded: they drifted to
        // read_file/list_files instead of generate_plan, so the forced tool is the ONLY option.
        for (Map.Entry<String, Set<String>> entry : STRONG_TOOL_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword)) {
                    selectedNames.add(entry.getKey());
                    break;
                }
            }
        }
        if (!selectedNames.isEmpty()) {
            // Pipeline/TDD workflow tools are multi-step: 'pipeline start_tdd' must also give the
            // model write_file/edit_file/execute_command/tdd_enforcer so it can truly implement the
            // step. Collapsing to exactly one tool is what previously forced fake TDD (model could
            // only call start_tdd -> complete_tdd, never writing any code). Single-tool forcing is
            // kept for document tools (generate_plan/export_plan) whose steps are single-purpose.
            if (selectedNames.contains("sdd_pipeline") || selectedNames.contains("tdd_enforcer")) {
                selectedNames.add("write_file");
                selectedNames.add("edit_file");
                selectedNames.add("tdd_enforcer");
                selectedNames.add("run_tests");
                selectedNames.add("run_gradle_task");
                selectedNames.add("execute_command");
                selectedNames.addAll(CORE_TOOLS);
            }
            if (selectedNames.size() == 1) {
                strongForcedTool = selectedNames.iterator().next();
            }
            return selectedNames.stream()
                    .map(toolMap::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        selectedNames.addAll(CORE_TOOLS);
        for (Map.Entry<String, Set<String>> entry : TOOL_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword)) {
                    selectedNames.add(entry.getKey());
                    break;
                }
            }
        }

        // Compound matching: when "change" appears, include all SDD change tools
        if (lower.contains("change") && !lower.contains("diff") && !lower.contains("git")) {
            selectedNames.add("read_change");
            selectedNames.add("create_change");
            selectedNames.add("list_changes");
            selectedNames.add("archive_change");
        }
        // "spec" or "openspec" -> all SDD tools
        if (lower.contains("spec") || lower.contains("openspec")) {
            selectedNames.add("sdd_status");
            selectedNames.add("list_specs");
            selectedNames.add("read_spec");
            selectedNames.add("list_changes");
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

    private static String truncateToolResult(String result) {
        if (result != null && result.length() > TOOL_RESULT_MAX_CHARS) {
            return result.substring(0, TOOL_RESULT_MAX_CHARS) + "\n... (result truncated)";
        }
        return result;
    }

    private static int trimContextWindow(List<ChatMessage> messages, int maxContextMessages) {
        if (messages.size() <= maxContextMessages) {
            return 0;
        }
        // Always keep the system message (index 0) plus the most recent messages,
        // so earlier intermediate tool interactions are consumed and dropped.
        int total = messages.size();
        int keepFrom = messages.size() - (maxContextMessages - 1);
        List<ChatMessage> retained = new ArrayList<>(messages.subList(0, 1));
        retained.addAll(messages.subList(Math.max(1, keepFrom), messages.size()));
        messages.clear();
        messages.addAll(retained);
        return total - messages.size();
    }

    public String execute(List<Conversation.ChatMessage> history, String modelName, AgentEvent event) {
        return execute(history, modelName, event, false);
    }

    public String executeStreaming(List<Conversation.ChatMessage> history, String modelName, AgentEvent event) {
        return execute(history, modelName, event, true);
    }

    private String execute(List<Conversation.ChatMessage> history, String modelName, AgentEvent event, boolean streaming) {
        cancelled = false;
        lastAnswer = null;
        strongForcedTool = null;
        model = createModel(modelName);
        streamingModel = streaming ? createStreamingModel(modelName) : null;

        String lastUserMsg = "";
        for (Conversation.ChatMessage msg : history) {
            if (msg.getRole() == Conversation.Role.USER) {
                lastUserMsg = msg.getContent();
            }
        }

        PluginStateService state = PluginStateService.getInstance();
        int maxIterations = maxIterationsOverride > 0
                ? maxIterationsOverride
                : state.getAgentMaxIterations();
        maxIterationsOverride = -1;
        int maxToolCalls = toolCallLimitOverride > 0
                ? toolCallLimitOverride
                : state.getAgentMaxToolCalls();
        toolCallLimitOverride = -1;
        int maxHistory = state.getAgentMaxHistory();

        String role = roleOverride != null ? roleOverride : detectRole(lastUserMsg);
        roleOverride = null;
        List<AgentTool> selectedTools = selectToolsForMessage(lastUserMsg);
        List<ToolSpecification> activeToolSpecs = selectedTools.stream()
                .map(AgentTool::specification)
                .collect(Collectors.toList());
        String toolList = selectedTools.stream().map(AgentTool::name).collect(Collectors.joining(", "));
        String memoryInjection = "";
        if (PluginStateService.getInstance().isMemoryEnabled() && !suppressMemoryInjectionOverride) {
            memoryInjection = memoryService.formatForSystemInjection(
                    PluginStateService.getInstance().getMemoryMaxInjection());
        }
        suppressMemoryInjectionOverride = false;
        SystemMessage systemMsg = buildSystemMessage(role, "\n\nAvailable tools: " + toolList + memoryInjection);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(systemMsg);

        // Limit history to last N messages to avoid context overflow
        List<Conversation.ChatMessage> limitedHistory = history;
        if (history.size() > maxHistory) {
            limitedHistory = history.subList(history.size() - maxHistory, history.size());
        }

        for (Conversation.ChatMessage msg : limitedHistory) {
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

        String lastCallKey = null;
        int consecutiveCallCount = 0;
        int totalToolCalls = 0;
        boolean isTddRun = selectedTools.stream().anyMatch(t -> t.name().equals("tdd_enforcer"));
        int explorationCalls = 0;
        boolean explorationForced = false;

        for (int i = 0; i < maxIterations && !cancelled; i++) {
            try {
                event.onThinking("Iteration " + (i + 1) + "/" + maxIterations);

                // Enforce a sliding window so accumulated tool interactions do not
                // outgrow the model's context (keeps system + recent history only).
                int dropped = trimContextWindow(messages, state.getAgentMaxContextMessages());
                if (dropped > 0) {
                    event.onContextTrim(dropped, messages.size(), state.getAgentMaxContextMessages());
                }

                ChatRequest request = ChatRequest.builder()
                        .messages(messages)
                        .toolSpecifications(activeToolSpecs)
                        .build();

                ChatResponse response = callModel(request, event, PluginStateService.getInstance().getTimeout());
                AiMessage aiMessage = response.aiMessage();

                if (aiMessage.hasToolExecutionRequests()) {
                    event.onThinking("Model requested " + aiMessage.toolExecutionRequests().size() + " tool call(s)");
                    messages.add(aiMessage);

                    for (ToolExecutionRequest toolReq : aiMessage.toolExecutionRequests()) {
                        if (cancelled) break;

                        String toolName = toolReq.name();
                        String args = toolReq.arguments();
                        totalToolCalls++;

                        if (totalToolCalls > maxToolCalls) {
                            event.onThinking("Total tool call limit reached (" + maxToolCalls + "), forcing final answer");
                            messages.add(UserMessage.from(
                                    "STOP. You have used all your tool calls. " +
                                    "Provide your final answer now based on the information you have gathered."));
                            break;
                        }

                        String callKey = toolName + ":" + args;
                        if (callKey.equals(lastCallKey)) {
                            consecutiveCallCount++;
                        } else {
                            lastCallKey = callKey;
                            consecutiveCallCount = 1;
                        }

                        if (consecutiveCallCount >= 3) {
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
                        messages.add(ToolExecutionResultMessage.from(toolReq, truncateToolResult(result)));

                        if (isTddRun && TDD_EXPLORATION_TOOLS.contains(toolName)) {
                            // Pure exploration: read_file/list_files/search_code/execute_command.
                            // AgentContext/other calls give these results already; a run that only
                            // explores never produces a test, so enforce a hard budget.
                            explorationCalls++;
                            if (!explorationForced && explorationCalls > TDD_EXPLORATION_BUDGET) {
                                explorationForced = true;
                                activeToolSpecs.removeIf(spec -> TDD_EXPLORATION_TOOLS.contains(spec.name()));
                                messages.add(UserMessage.from(
                                        "EXPLORATION BUDGET EXHAUSTED (" + TDD_EXPLORATION_BUDGET + " calls). "
                                        + "The relevant source is already embedded in the task instructions. "
                                        + "STOP reading files. Your next tool call MUST make progress: "
                                        + "write_file/edit_file (test or implementation), run_tests/run_gradle_task, "
                                        + "or tdd_enforcer (verify_test/verify_code/mark_refactor)."));
                                event.onThinking("TDD exploration budget exceeded (" + TDD_EXPLORATION_BUDGET
                                        + "), exploration tools removed from this run");
                                break;
                            }
                        } else if (isTddRun && explorationForced) {
                            // A non-exploration call succeeded (wrote a file, ran tests, advanced
                            // the enforcer) => that TDD phase made progress; allow reading again
                            // so the next phase (e.g. GREEN) can inspect the code it must change.
                            activeToolSpecs = selectedTools.stream()
                                    .map(AgentTool::specification)
                                    .collect(Collectors.toList());
                            explorationForced = false;
                            explorationCalls = 0;
                            event.onThinking("TDD progress detected, exploration tools restored");
                        }
                    }
                } else {
                    String text = aiMessage.text();
                    if (i == 0 && strongForcedTool != null) {
                        event.onThinking("Strong tool '" + strongForcedTool + "' forced but model returned text without calling it; nudging to invoke the tool");
                        messages.add(UserMessage.from(
                                "The available tool '" + strongForcedTool + "' is REQUIRED for this request. " +
                                "Do NOT answer in plain text. Call '" + strongForcedTool + "' with the appropriate action now."));
                        continue;
                    }
                    if (text != null && !text.isEmpty()) {
                        if (streaming) {
                            lastAnswer = text;
                            return text;
                        }
                        event.onAnswer(text);
                    }
                    lastAnswer = text;
                    return text;
                }
            } catch (java.util.concurrent.CancellationException e) {
                if (!cancelled) {
                    throw e;
                }
                break;
            } catch (Exception e) {
                String error = "Agent error: " + e.getMessage();
                if (e instanceof java.net.ConnectException) {
                    String url = PluginStateService.getInstance().getLlamaCppUrl();
                    error = AgentErrorMessages.connectionError(url, e.getMessage());
                } else {
                    String msg = e.getMessage();
                    if (msg != null && (msg.contains("exceed_context_size_error")
                            || msg.contains("context window")
                            || msg.contains("context length")
                            || msg.contains("maximum context length")
                            || msg.contains("too many tokens")
                            || msg.contains("tokens exceeds"))) {
                        error = "Agent error: " + AgentErrorMessages.contextLimitError()
                                + "\nOriginal error: " + msg;
                    }
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
        CompletableFuture<ChatResponse> call = inFlightCall;
        if (call != null) {
            call.cancel(true);
        }
        StreamingChatModel m = streamingModel;
        if (m != null) {
            streamingModel = null;
            if (m instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public String getLastAnswer() {
        return lastAnswer;
    }

    public List<String> getToolNames() {
        return tools.stream().map(AgentTool::name).toList();
    }
}
