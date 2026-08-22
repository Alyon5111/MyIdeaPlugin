package com.example.myplugin.chatmodel.local;

import com.example.myplugin.chatmodel.ChatModelFactory;
import com.example.myplugin.chatmodel.local.llamacpp.LlamaModelService;
import com.example.myplugin.model.CustomChatModel;
import com.example.myplugin.model.LanguageModel;
import com.example.myplugin.settings.PluginStateService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;

public class LlamaCppChatModelFactory implements ChatModelFactory {

    @Override
    public ChatModel createChatModel(@NotNull CustomChatModel customChatModel) {
        PluginStateService state = PluginStateService.getInstance();
        return OpenAiChatModel.builder()
                .baseUrl(state.getLlamaCppUrl())
                .apiKey(state.getApiKey())
                .modelName(customChatModel.getModelName())
                .temperature(customChatModel.getTemperature())
                .topP(customChatModel.getTopP())
                .maxRetries(customChatModel.getMaxRetries())
                .timeout(Duration.ofSeconds(customChatModel.getTimeout()))
                .build();
    }

    @Override
    public StreamingChatModel createStreamingChatModel(@NotNull CustomChatModel customChatModel) {
        PluginStateService state = PluginStateService.getInstance();
        return OpenAiStreamingChatModel.builder()
                .baseUrl(state.getLlamaCppUrl())
                .apiKey(state.getApiKey())
                .modelName(customChatModel.getModelName())
                .temperature(customChatModel.getTemperature())
                .topP(customChatModel.getTopP())
                .timeout(Duration.ofSeconds(customChatModel.getTimeout()))
                .build();
    }

    @Override
    public List<LanguageModel> getModels() {
        try {
            return LlamaModelService.getInstance().getModels();
        } catch (Exception e) {
            return List.of(LanguageModel.builder()
                    .provider(com.example.myplugin.model.ModelProvider.LLaMA)
                    .modelName("default")
                    .displayName("default (Llama.cpp not running)")
                    .inputCost(0)
                    .outputCost(0)
                    .inputMaxTokens(8192)
                    .apiKeyUsed(false)
                    .build());
        }
    }
}
