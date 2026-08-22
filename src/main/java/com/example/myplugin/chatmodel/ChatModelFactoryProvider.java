package com.example.myplugin.chatmodel;

import com.example.myplugin.chatmodel.local.LlamaCppChatModelFactory;
import com.example.myplugin.model.ModelProvider;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class ChatModelFactoryProvider {

    private ChatModelFactoryProvider() {
    }

    private static final Map<ModelProvider, Supplier<ChatModelFactory>> FACTORY_SUPPLIERS =
            new EnumMap<>(ModelProvider.class);

    static {
        FACTORY_SUPPLIERS.put(ModelProvider.LLaMA, LlamaCppChatModelFactory::new);
    }

    private static final Map<ModelProvider, ChatModelFactory> factoryCache = new EnumMap<>(ModelProvider.class);

    public static Optional<ChatModelFactory> getFactoryByProvider(ModelProvider provider) {
        Supplier<ChatModelFactory> supplier = FACTORY_SUPPLIERS.get(provider);
        if (supplier == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(factoryCache.computeIfAbsent(provider, p -> supplier.get()));
    }
}
