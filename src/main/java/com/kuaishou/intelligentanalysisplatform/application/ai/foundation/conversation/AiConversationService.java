package com.kuaishou.intelligentanalysisplatform.application.ai.foundation.conversation;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.application.ai.foundation.provider.AiModelProvider;
import com.kuaishou.intelligentanalysisplatform.domain.conversation.Conversation;

public interface AiConversationService {

    Conversation getOrCreate(String conversationId, String tenantId, String userId);

    List<AiModelProvider.AiMessage> prepareAndSave(Conversation conversation, String systemPrompt, String userMessage);

    void appendAssistantReply(String conversationId, String tenantId, String userId, String reply);
}
