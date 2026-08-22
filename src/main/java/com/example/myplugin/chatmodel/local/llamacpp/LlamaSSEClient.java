package com.example.myplugin.chatmodel.local.llamacpp;

import com.example.myplugin.settings.PluginStateService;
import com.google.gson.Gson;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.List;

public class LlamaSSEClient {

    private static final Gson GSON = new Gson();
    private static final LlamaSSEClient INSTANCE = new LlamaSSEClient();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "llama-sse-client");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean running = false;
    private volatile Thread readerThread;

    private final ConcurrentHashMap<String, String> modelStatuses = new ConcurrentHashMap<>();
    private volatile CountDownLatch connectedLatch;
    private volatile CountDownLatch loadLatch;
    private volatile String loadLatchModel;
    private volatile String lastError;

    private ProgressCallback progressCallback;

    private LlamaSSEClient() {}

    public static LlamaSSEClient getInstance() {
        return INSTANCE;
    }

    public void prepareWait(String modelName) {
        loadLatch = new CountDownLatch(1);
        loadLatchModel = modelName;
    }

    public boolean waitForModel(int timeoutSeconds) {
        CountDownLatch latch = loadLatch;
        if (latch == null) return true;
        try {
            return latch.await(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void connect() {
        disconnect();
        running = true;
        lastError = null;
        connectedLatch = new CountDownLatch(1);

        executor.submit(() -> {
            readerThread = Thread.currentThread();
            PluginStateService state = PluginStateService.getInstance();
            String baseUrl = state.getLlamaCppUrl();

            while (running) {
                try {
                    HttpClient client = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(5))
                            .build();

                    String url = baseUrl + "/models/sse";
                    HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(10))
                            .header("Accept", "text/event-stream")
                            .GET();

                    if (state.getApiKey() != null && !state.getApiKey().isEmpty()) {
                        reqBuilder.header("Authorization", "Bearer " + state.getApiKey());
                    }

                    HttpResponse<InputStream> response = client.send(
                            reqBuilder.build(),
                            HttpResponse.BodyHandlers.ofInputStream());

                    int statusCode = response.statusCode();
                    if (statusCode != 200) {
                        String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                        lastError = "SSE endpoint returned HTTP " + statusCode + ": " + body;
                        System.err.println("[LlamaSSE] " + lastError);
                        // Count down so caller knows connection failed
                        if (connectedLatch != null) {
                            connectedLatch.countDown();
                            connectedLatch = null;
                        }
                        running = false;
                        return;
                    }

                    if (connectedLatch != null) {
                        connectedLatch.countDown();
                        connectedLatch = null;
                    }

                    parseSSEStream(response.body());

                } catch (IOException e) {
                    lastError = "SSE connection failed: " + e.getMessage();
                    System.err.println("[LlamaSSE] " + lastError);
                    if (running) {
                        try { Thread.sleep(2000); } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    lastError = "SSE error: " + e.getMessage();
                    System.err.println("[LlamaSSE] " + lastError);
                    if (running) {
                        try { Thread.sleep(2000); } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            running = false;
        });
    }

    private void parseSSEStream(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            StringBuilder dataBuffer = new StringBuilder();
            String line;

            while (running && (line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    dataBuffer.append(line.substring(6));
                } else if (line.isEmpty()) {
                    if (dataBuffer.length() > 0) {
                        try {
                            JsonObject json = GSON.fromJson(dataBuffer.toString(), JsonObject.class);
                            processEvent(json);
                        } catch (Exception ignored) {
                        }
                    }
                    dataBuffer.setLength(0);
                }
            }
        }
    }

    private void processEvent(JsonObject json) {
        try {
            String model = json.has("model") ? json.get("model").getAsString() : "";
            if (model.isEmpty()) return;

            JsonObject data = json.has("data") ? json.getAsJsonObject("data") : null;
            if (data == null) return;

            String status = data.has("status") ? data.get("status").getAsString() : "";
            if (status.isEmpty()) return;

            modelStatuses.put(model, status);

            if (progressCallback != null) {
                LoadProgress progress = new LoadProgress();
                progress.status = status;
                progress.model = model;

                if ("loaded".equals(status) || "sleeping".equals(status)) {
                    progress.value = 1.0;
                }

                if (data.has("progress")) {
                    JsonObject p = data.getAsJsonObject("progress");
                    if (p.has("value")) {
                        progress.value = p.get("value").getAsDouble();
                    }
                    if (p.has("current")) {
                        progress.currentStage = p.get("current").getAsString();
                    }
                    if (p.has("stages")) {
                        progress.stages = new java.util.ArrayList<>();
                        p.getAsJsonArray("stages").forEach(e ->
                                progress.stages.add(e.getAsString()));
                    }
                }

                progressCallback.onProgress(progress);
            }

            CountDownLatch latch = loadLatch;
            String latchModel = loadLatchModel;
            if (latch != null && latchModel != null) {
                if (model.equals(latchModel) || model.contains(latchModel) || latchModel.contains(model)) {
                    if ("loaded".equals(status) || "sleeping".equals(status)) {
                        loadLatch = null;
                        loadLatchModel = null;
                        latch.countDown();
                    }
                }
            }

        } catch (Exception ignored) {
        }
    }

    public void setProgressCallback(ProgressCallback callback) {
        this.progressCallback = callback;
    }

    public void disconnect() {
        running = false;
        connectedLatch = null;
        Thread t = readerThread;
        if (t != null) {
            t.interrupt();
        }
    }

    public boolean isConnected() {
        return running;
    }

    public boolean waitForConnection(int timeoutSeconds) {
        CountDownLatch latch = connectedLatch;
        if (latch == null) return true;
        try {
            return latch.await(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public String getLastError() {
        return lastError;
    }

    public String getModelStatus(String modelName) {
        return modelStatuses.getOrDefault(modelName, "unknown");
    }

    public void clearStatuses() {
        modelStatuses.clear();
    }

    public interface ProgressCallback {
        void onProgress(LoadProgress progress);
    }

    public static class LoadProgress {
        public String model = "";
        public String status = "";
        public double value = 0;
        public String currentStage = "";
        public List<String> stages = new java.util.ArrayList<>();

        public int getPercent() {
            return (int) (value * 100);
        }

        public int getStageIndex() {
            return stages.indexOf(currentStage);
        }

        public int getStageCount() {
            return stages.size();
        }

        @Override
        public String toString() {
            return "LoadProgress{model='" + model + "', status='" + status +
                    "', stage='" + currentStage + "' (" + getStageIndex() + "/" + getStageCount() +
                    "), value=" + getPercent() + "%}";
        }
    }
}
