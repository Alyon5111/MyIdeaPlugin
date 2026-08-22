package com.example.myplugin.chatmodel;

import com.example.myplugin.model.CustomChatModel;
import com.example.myplugin.model.LanguageModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;

import java.util.List;

public interface ChatModelFactory {

    ChatModel createChatModel(CustomChatModel customChatModel);

    default StreamingChatModel createStreamingChatModel(CustomChatModel customChatModel) {
        return null;
    }

    List<LanguageModel> getModels();
}
