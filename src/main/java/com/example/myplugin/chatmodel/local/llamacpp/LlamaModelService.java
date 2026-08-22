package com.example.myplugin.chatmodel.local.llamacpp;

import com.example.myplugin.model.LanguageModel;
import com.example.myplugin.model.ModelProvider;
import com.example.myplugin.settings.PluginStateService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class LlamaModelService {

    private static final Gson GSON = new Gson();
    private static final LlamaModelService INSTANCE = new LlamaModelService();
    private List<LanguageModel> cachedModels = null;

    private LlamaModelService() {
    }

    public static LlamaModelService getInstance() {
        return INSTANCE;
    }

    public List<LanguageModel> getModels() throws IOException {
        if (cachedModels != null) {
            return cachedModels;
        }

        PluginStateService state = PluginStateService.getInstance();
        String baseUrl = state.getLlamaCppUrl();
        List<LanguageModel> models = new ArrayList<>();

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/models"))
                    .timeout(Duration.ofSeconds(5))
                    .GET();
            if (state.getApiKey() != null && !state.getApiKey().isEmpty()) {
                reqBuilder.header("Authorization", "Bearer " + state.getApiKey());
            }
            HttpRequest request = reqBuilder.build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("[LlamaModel] /v1/models response: " + response.body());
            JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
            JsonArray data = json.getAsJsonArray("data");

            if (data != null) {
                for (int i = 0; i < data.size(); i++) {
                    JsonObject model = data.get(i).getAsJsonObject();
                    String id = model.get("id").getAsString();
                    String status = "";
                    if (model.has("status") && model.get("status").isJsonObject()) {
                        JsonObject statusObj = model.getAsJsonObject("status");
                        status = statusObj.has("value") ? statusObj.get("value").getAsString() : "";
                    }
                    int contextWindow = extractContextWindow(model);
                    models.add(LanguageModel.builder()
                            .provider(ModelProvider.LLaMA)
                            .modelName(id)
                            .displayName(id)
                            .status(status)
                            .inputCost(0)
                            .outputCost(0)
                            .inputMaxTokens(contextWindow)
                            .apiKeyUsed(false)
                            .build());
                }
            }
        } catch (IOException | InterruptedException e) {
            throw new IOException("Failed to fetch models from Llama.cpp: " + e.getMessage(), e);
        }

        if (models.isEmpty()) {
            models.add(LanguageModel.builder()
                    .provider(ModelProvider.LLaMA)
                    .modelName("default")
                    .displayName("default")
                    .inputCost(0)
                    .outputCost(0)
                    .inputMaxTokens(8192)
                    .apiKeyUsed(false)
                    .build());
        }

        cachedModels = models;
        return cachedModels;
    }

    public void resetModels() {
        cachedModels = null;
    }

    /**
     * Load a model via POST {baseUrl}/v1/models/load
     * Request body: {"model":"modelName"}
     */
    public String loadModel(String modelName) throws IOException {
        PluginStateService state = PluginStateService.getInstance();
        String baseUrl = state.getLlamaCppUrl();

        try {
            JsonObject body = new JsonObject();
            body.addProperty("model", modelName);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/models/load"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json");
            if (state.getApiKey() != null && !state.getApiKey().isEmpty()) {
                reqBuilder.header("Authorization", "Bearer " + state.getApiKey());
            }
            HttpRequest request = reqBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            resetModels();
            return response.body();
        } catch (IOException | InterruptedException e) {
            throw new IOException("Failed to load model: " + e.getMessage(), e);
        }
    }

    private int extractContextWindow(JsonObject model) {
        try {
            if (model.has("context_length")) {
                return model.get("context_length").getAsInt();
            }
            if (model.has("meta") && model.getAsJsonObject("meta").has("context_length")) {
                return model.getAsJsonObject("meta").get("context_length").getAsInt();
            }
        } catch (Exception ignored) {
        }
        return 8192;
    }
}
