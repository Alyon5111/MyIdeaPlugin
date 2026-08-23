package com.example.myplugin.agent;

import com.example.myplugin.agent.tools.*;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class AgentExecutor {

    private static final int MAX_ITERATIONS = 15;
    private static final Gson GSON = new Gson();
    private static final SystemMessage SYSTEM_MESSAGE = SystemMessage.from(
            "You are an expert coding agent embedded in an IntelliJ IDEA plugin. " +
            "You can read, write, and edit files, search code, run commands, and analyze Java code. " +
            "Always think step by step before acting. Use tools to explore the codebase first, " +
            "then make targeted changes. Explain what you're doing and why. " +
            "Keep file paths relative to the project root. " +
            "Operating system: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + ". " +
            "Use OS-appropriate commands.");

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
                new FindReferencesTool(ctx)
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
        return OpenAiChatModel.builder()
                .baseUrl(state.getLlamaCppUrl())
                .apiKey(state.getApiKey())
                .modelName(modelName)
                .temperature(0.3)
                .topP(0.9)
                .maxRetries(1)
                .timeout(Duration.ofSeconds(300))
                .build();
    }

    public String execute(List<Conversation.ChatMessage> history, String modelName, AgentEvent event) {
        cancelled = false;
        lastAnswer = null;
        model = createModel(modelName);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SYSTEM_MESSAGE);

        for (Conversation.ChatMessage msg : history) {
            if (msg.getRole() == Conversation.Role.USER) {
                messages.add(UserMessage.from(msg.getContent()));
            } else if (msg.getRole() == Conversation.Role.ASSISTANT) {
                messages.add(AiMessage.from(msg.getContent()));
            }
        }

        for (int i = 0; i < MAX_ITERATIONS && !cancelled; i++) {
            try {
                ChatRequest request = ChatRequest.builder()
                        .messages(messages)
                        .toolSpecifications(toolSpecs)
                        .build();

                ChatResponse response = model.chat(request);
                AiMessage aiMessage = response.aiMessage();

                if (aiMessage.hasToolExecutionRequests()) {
                    messages.add(aiMessage);

                    for (ToolExecutionRequest toolReq : aiMessage.toolExecutionRequests()) {
                        if (cancelled) break;

                        String toolName = toolReq.name();
                        String args = toolReq.arguments();
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
