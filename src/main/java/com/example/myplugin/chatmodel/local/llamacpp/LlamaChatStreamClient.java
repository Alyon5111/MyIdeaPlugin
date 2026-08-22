package com.example.myplugin.chatmodel.local.llamacpp;

import com.example.myplugin.model.Conversation;
import com.example.myplugin.settings.PluginStateService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class LlamaChatStreamClient {

    private static final Gson GSON = new Gson();

    public static void streamChat(String modelName, List<Conversation.ChatMessage> messages,
                                  BiConsumer<String, String> onDelta,
                                  BiConsumer<String, String> onComplete,
                                  BiConsumer<Boolean, String> onError) {

        PluginStateService state = PluginStateService.getInstance();
        String baseUrl = state.getLlamaCppUrl();

        new Thread(() -> {
            try {
                JsonObject requestBody = buildRequest(modelName, messages, state);

                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(state.getTimeout()))
                        .build();

                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/v1/chat/completions"))
                        .timeout(Duration.ofSeconds(state.getTimeout() + 30))
                        .header("Content-Type", "application/json")
                        .header("Accept", "text/event-stream")
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)));

                if (state.getApiKey() != null && !state.getApiKey().isEmpty()) {
                    reqBuilder.header("Authorization", "Bearer " + state.getApiKey());
                }

                HttpResponse<InputStream> response = client.send(
                        reqBuilder.build(),
                        HttpResponse.BodyHandlers.ofInputStream());

                int statusCode = response.statusCode();
                if (statusCode != 200) {
                    String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                    onError.accept(false, "HTTP " + statusCode + ": " + body);
                    return;
                }

                parseSSEStream(response.body(), onDelta, onComplete, onError);

            } catch (Exception e) {
                onError.accept(false, e.getMessage());
            }
        }, "llama-chat-stream").start();
    }

    private static JsonObject buildRequest(String modelName, List<Conversation.ChatMessage> messages,
                                           PluginStateService state) {
        JsonObject body = new JsonObject();
        body.addProperty("model", modelName);
        body.addProperty("stream", true);
        body.addProperty("temperature", state.getTemperature());
        body.addProperty("top_p", state.getTopP());
        body.addProperty("max_tokens", state.getMaxTokens());

        JsonArray msgs = new JsonArray();
        for (Conversation.ChatMessage msg : messages) {
            JsonObject m = new JsonObject();
            m.addProperty("role", msg.getRole() == Conversation.Role.USER ? "user" : "assistant");
            m.addProperty("content", msg.getContent());
            msgs.add(m);
        }
        body.add("messages", msgs);

        return body;
    }

    private static void parseSSEStream(InputStream inputStream,
                                       BiConsumer<String, String> onDelta,
                                       BiConsumer<String, String> onComplete,
                                       BiConsumer<Boolean, String> onError) throws IOException {
        StringBuilder reasoning = new StringBuilder();
        StringBuilder content = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            StringBuilder dataBuffer = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    dataBuffer.append(line.substring(6));
                } else if (line.isEmpty()) {
                    if (dataBuffer.length() > 0) {
                        String data = dataBuffer.toString().trim();
                        if ("[DONE]".equals(data)) {
                            onComplete.accept(reasoning.toString(), content.toString());
                            return;
                        }
                        try {
                            JsonObject json = GSON.fromJson(data, JsonObject.class);
                            JsonObject delta = extractDelta(json);
                            if (delta != null) {
                                if (delta.has("reasoning_content") && !delta.get("reasoning_content").isJsonNull()) {
                                    String text = delta.get("reasoning_content").getAsString();
                                    reasoning.append(text);
                                    onDelta.accept("reasoning", text);
                                }
                                if (delta.has("content") && !delta.get("content").isJsonNull()) {
                                    String text = delta.get("content").getAsString();
                                    content.append(text);
                                    onDelta.accept("content", text);
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    dataBuffer.setLength(0);
                }
            }
        }

        onComplete.accept(reasoning.toString(), content.toString());
    }

    private static JsonObject extractDelta(JsonObject json) {
        try {
            JsonArray choices = json.getAsJsonArray("choices");
            if (choices != null && choices.size() > 0) {
                JsonObject choice = choices.get(0).getAsJsonObject();
                if (choice.has("delta")) {
                    return choice.getAsJsonObject("delta");
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
