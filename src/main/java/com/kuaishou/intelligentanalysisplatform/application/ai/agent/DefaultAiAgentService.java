package com.kuaishou.intelligentanalysisplatform.application.ai.agent;

import java.util.List;
import java.util.UUID;

import com.kuaishou.intelligentanalysisplatform.application.ai.foundation.ConversationalStreamExecutor;
import com.kuaishou.intelligentanalysisplatform.application.ai.foundation.conversation.AiConversationService;
import com.kuaishou.intelligentanalysisplatform.application.ai.foundation.provider.AiModelProvider;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiChatRequestDTO;
import com.kuaishou.intelligentanalysisplatform.domain.conversation.Conversation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DefaultAiAgentService implements AiAgentService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiAgentService.class);
    private static final String CHAT_SYSTEM_PROMPT = "You are a helpful assistant.";

    private final AiConversationService aiConversationService;
    private final ConversationalStreamExecutor streamExecutor;

    public DefaultAiAgentService(AiConversationService aiConversationService,
                                 ConversationalStreamExecutor streamExecutor) {
        this.aiConversationService = aiConversationService;
        this.streamExecutor = streamExecutor;
    }

    @Override
    public AiAgentTask createPlaceholderTask(String capability) {
        return new AiAgentTask(UUID.randomUUID().toString(), AiAgentTaskStatus.INIT, capability);
    }

    @Override
    public void streamChat(AiChatRequestDTO request, RequestContextDTO context, ChatStreamHandler handler) {
        try {
            Conversation conversation = aiConversationService.getOrCreate(
                    request.getConversationId(), context.getTenantId(), context.getUserId());
            List<AiModelProvider.AiMessage> history = aiConversationService.prepareAndSave(
                    conversation, CHAT_SYSTEM_PROMPT, request.getPrompt());

            streamExecutor.streamAndSave(
                    new AiModelProvider.AiChatRequest(CHAT_SYSTEM_PROMPT, request.getPrompt(), history),
                    conversation,
                    context.getTenantId(), context.getUserId(),
                    handler);
        } catch (Exception e) {
            log.error("Failed to start AI chat", e);
            handler.onError(e.getMessage());
        }
    }
}
