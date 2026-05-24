package com.kuaishou.intelligentanalysisplatform.application.ai.foundation.provider;

import java.util.List;

public interface AiModelProvider {

    void streamChat(AiChatRequest request, AiStreamCallbacks callbacks);

    String completeChat(AiChatRequest request);

    String providerType();

    record AiChatRequest(String systemPrompt, String userMessage, List<AiMessage> history) {
    }

    record AiMessage(String role, String content) {
    }
}
