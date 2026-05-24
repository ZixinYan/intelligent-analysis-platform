package com.kuaishou.intelligentanalysisplatform.application.ai.foundation;

import com.kuaishou.intelligentanalysisplatform.application.ai.foundation.conversation.AiConversationService;
import com.kuaishou.intelligentanalysisplatform.application.ai.foundation.provider.AiModelProvider;
import com.kuaishou.intelligentanalysisplatform.application.ai.foundation.provider.AiStreamCallbacks;
import com.kuaishou.intelligentanalysisplatform.domain.conversation.Conversation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 通用对话流式执行器。
 *
 * <p>封装「调用 LLM 流式输出 → 收集 token → 保存 assistant 回复到会话」的共性流程，
 * 供 SQL 生成、Chat 等多个场景复用，避免重复代码。
 */
@Component
public class ConversationalStreamExecutor {

    private static final Logger log = LoggerFactory.getLogger(ConversationalStreamExecutor.class);

    private final AiModelProvider aiModelProvider;
    private final AiConversationService aiConversationService;

    public ConversationalStreamExecutor(AiModelProvider aiModelProvider,
                                        AiConversationService aiConversationService) {
        this.aiModelProvider = aiModelProvider;
        this.aiConversationService = aiConversationService;
    }

    /**
     * 流式调用 LLM，并在完成后将 assistant 回复持久化到会话。
     *
     * @param request      包含 systemPrompt / userMessage / history 的请求
     * @param conversation 当前会话（用于获取 conversationId 并持久化回复）
     * @param tenantId     租户 ID
     * @param userId       用户 ID
     * @param handler      流式事件回调
     */
    public void streamAndSave(AiModelProvider.AiChatRequest request,
                              Conversation conversation,
                              String tenantId, String userId,
                              StreamHandler handler) {
        StringBuilder buffer = new StringBuilder();
        aiModelProvider.streamChat(request, new AiStreamCallbacks(
                token -> {
                    buffer.append(token);
                    handler.onToken(token);
                },
                () -> {
                    aiConversationService.appendAssistantReply(
                            conversation.getConversationId(), tenantId, userId, buffer.toString());
                    handler.onDone(conversation.getConversationId());
                },
                error -> {
                    log.error("AI stream failed", error);
                    handler.onError(error.getMessage());
                }
        ));
    }

    public interface StreamHandler {
        void onToken(String token);

        void onDone(String conversationId);

        void onError(String message);
    }
}
