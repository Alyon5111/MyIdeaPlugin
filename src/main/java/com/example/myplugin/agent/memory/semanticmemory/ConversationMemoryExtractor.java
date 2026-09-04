package com.example.myplugin.agent.memory.semanticmemory;

import com.example.myplugin.model.Conversation;
import com.example.myplugin.settings.PluginStateService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConversationMemoryExtractor {

    private static final Pattern FACT_PATTERN = Pattern.compile("FACT\\|(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DECISION_PATTERN = Pattern.compile("DECISION\\|(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PREFERENCE_PATTERN = Pattern.compile("PREFERENCE\\|(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONVENTION_PATTERN = Pattern.compile("CONVENTION\\|(.+)", Pattern.CASE_INSENSITIVE);

    public static class ExtractedFact {
        private final com.example.myplugin.agent.memory.MemoryCategory category;
        private final String content;
        private final double confidence;

        public ExtractedFact(com.example.myplugin.agent.memory.MemoryCategory category, String content, double confidence) {
            this.category = category;
            this.content = content;
            this.confidence = confidence;
        }

        public com.example.myplugin.agent.memory.MemoryCategory getCategory() { return category; }
        public String getContent() { return content; }
        public double getConfidence() { return confidence; }
    }

    public List<ExtractedFact> extractFromConversation(Conversation conversation) {
        // Try LLM extraction first, fall back to rule-based
        try {
            String llmResult = tryLlmExtraction(conversation);
            if (llmResult != null && !llmResult.trim().isEmpty()) {
                List<ExtractedFact> facts = parseExtractionResult(llmResult);
                if (!facts.isEmpty()) {
                    return facts;
                }
            }
        } catch (Exception e) {
            // Fall through to rule-based
        }
        return ruleBasedExtraction(conversation);
    }

    private String tryLlmExtraction(Conversation conversation) {
        if (!PluginStateService.getInstance().isMemoryExtractionEnabled()) {
            return null;
        }

        PluginStateService state = PluginStateService.getInstance();
        String url = state.getLlamaCppUrl();
        if (url == null || url.isEmpty()) {
            url = "http://localhost:8080";
        }
        if (!url.endsWith("/v1")) {
            url = url.replaceAll("/+$", "") + "/v1";
        }

        ChatModel model = OpenAiChatModel.builder()
                .baseUrl(url)
                .apiKey(state.getApiKey() != null ? state.getApiKey() : "no-key")
                .modelName("local-model")
                .temperature(0.0)
                .maxRetries(1)
                .build();

        StringBuilder dialog = new StringBuilder();
        for (Conversation.ChatMessage msg : conversation.getMessages()) {
            dialog.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }

        String prompt = """
            Analyze this conversation and extract durable facts, decisions, preferences, and conventions that would be useful to remember for future sessions.

            For each item output on its own line prefixed with the type:
            FACT|content
            DECISION|content
            PREFERENCE|content
            CONVENTION|content

            Only include stable, reusable knowledge. Skip transient chit-chat.
            If nothing worth remembering, output nothing.

            Conversation:
            """ + dialog;

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(UserMessage.from(prompt));

        try {
            ChatRequest request = ChatRequest.builder().messages(messages).build();
            ChatResponse response = model.chat(request);
            AiMessage aiMessage = response.aiMessage();
            return aiMessage != null ? aiMessage.text() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private List<ExtractedFact> parseExtractionResult(String result) {
        List<ExtractedFact> facts = new ArrayList<>();
        if (result == null) return facts;

        for (String line : result.split("\\n")) {
            line = line.trim();
            Matcher m;
            if ((m = FACT_PATTERN.matcher(line)).matches()) {
                facts.add(new ExtractedFact(com.example.myplugin.agent.memory.MemoryCategory.FACT, m.group(1).trim(), 0.8));
            } else if ((m = DECISION_PATTERN.matcher(line)).matches()) {
                facts.add(new ExtractedFact(com.example.myplugin.agent.memory.MemoryCategory.DECISION, m.group(1).trim(), 0.95));
            } else if ((m = PREFERENCE_PATTERN.matcher(line)).matches()) {
                facts.add(new ExtractedFact(com.example.myplugin.agent.memory.MemoryCategory.PREFERENCE, m.group(1).trim(), 0.85));
            } else if ((m = CONVENTION_PATTERN.matcher(line)).matches()) {
                facts.add(new ExtractedFact(com.example.myplugin.agent.memory.MemoryCategory.CONVENTION, m.group(1).trim(), 0.9));
            }
        }
        return facts;
    }

    private List<ExtractedFact> ruleBasedExtraction(Conversation conversation) {
        List<ExtractedFact> facts = new ArrayList<>();
        List<String> keywords = List.of(
            "we use", "project uses", "the project", "we'll use", "we decided",
            "our convention", "i prefer", "we prefer", "stack is", "built with",
            "based on", "this project", "the codebase"
        );

        for (Conversation.ChatMessage msg : conversation.getMessages()) {
            if (msg.getRole() != Conversation.Role.USER && msg.getRole() != Conversation.Role.ASSISTANT) continue;
            String text = msg.getContent();
            if (text == null || text.isEmpty()) continue;

            for (String keyword : keywords) {
                int idx = text.toLowerCase().indexOf(keyword);
                if (idx >= 0) {
                    String sentence = extractSentence(text, idx);
                    if (sentence.length() > 15 && sentence.length() < 200) {
                        com.example.myplugin.agent.memory.MemoryCategory cat =
                            keyword.contains("decided") ? com.example.myplugin.agent.memory.MemoryCategory.DECISION :
                            keyword.contains("convention") ? com.example.myplugin.agent.memory.MemoryCategory.CONVENTION :
                            keyword.contains("prefer") ? com.example.myplugin.agent.memory.MemoryCategory.PREFERENCE :
                            com.example.myplugin.agent.memory.MemoryCategory.FACT;
                        facts.add(new ExtractedFact(cat, sentence, 0.6));
                    }
                    break;
                }
            }
        }
        return facts;
    }

    private String extractSentence(String text, int startIdx) {
        if (startIdx < 0) return "";
        int end = text.indexOf('.', startIdx);
        int endQ = text.indexOf('!', startIdx);
        int end2 = text.indexOf('?', startIdx);
        int best = Integer.MAX_VALUE;
        if (end >= 0) best = Math.min(best, end);
        if (endQ >= 0) best = Math.min(best, endQ);
        if (end2 >= 0) best = Math.min(best, end2);

        int actualEnd = (best == Integer.MAX_VALUE) ? text.length() : Math.min(best + 1, text.length());
        String sentence = text.substring(startIdx, actualEnd).trim();
        // Strip leading keyword
        int kwEnd = sentence.indexOf(' ');
        if (kwEnd > 0) {
            sentence = sentence.substring(kwEnd + 1).trim();
        }
        return sentence;
    }
}
