package com.kuaishou.intelligentanalysisplatform.application.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.domain.conversation.Conversation;
import com.kuaishou.intelligentanalysisplatform.domain.conversation.ConversationMessage;
import com.kuaishou.intelligentanalysisplatform.domain.conversation.ConversationRepository;
import com.kuaishou.intelligentanalysisplatform.domain.conversation.ConversationRole;
import org.springframework.stereotype.Service;

@Service
public class ConversationContextService {

    private static final int HISTORY_TOKEN_BUDGET = 3000;

    private final ConversationRepository repository;

    public ConversationContextService(ConversationRepository repository) {
        this.repository = repository;
    }

    /**
     * 获取或创建会话。conversationId 为 null/空时自动创建新会话。
     */
    public Conversation getOrCreate(String conversationId, String tenantId, String userId) {
        if (conversationId == null || conversationId.isBlank()) {
            Conversation conv = Conversation.builder()
                    .conversationId(UUID.randomUUID().toString())
                    .tenantId(tenantId)
                    .userId(userId)
                    .messages(new ArrayList<>())
                    .createdAt(System.currentTimeMillis())
                    .build();
            return repository.save(conv);
        }
        return repository.findById(conversationId)
                .orElseThrow(() -> new BaseBusinessException(
                        ErrorCode.CONVERSATION_NOT_FOUND, "conversationId not found: " + conversationId));
    }

    /**
     * 将本轮 system prompt 和用户消息追加到历史并持久化，返回供 LLM 使用的 messages 列表。
     */
    public List<Map<String, String>> prepareAndSave(Conversation conv,
                                                     String systemPrompt,
                                                     String userMessage) {
        // system 消息只在首次追加
        if (conv.getMessages().isEmpty()) {
            conv.getMessages().add(ConversationMessage.builder()
                    .role(ConversationRole.SYSTEM)
                    .content(systemPrompt)
                    .timestamp(System.currentTimeMillis())
                    .estimatedTokens(systemPrompt.length() / 4 + 1)
                    .build());
        }
        conv.appendUser(userMessage);
        repository.save(conv);

        return conv.getWindowedHistory(HISTORY_TOKEN_BUDGET).stream()
                .map(m -> Map.of("role", m.getRole().name().toLowerCase(), "content", m.getContent()))
                .toList();
    }

    /**
     * AI 回复完成后追加并持久化。
     */
    public void appendAssistantReply(String conversationId, String reply) {
        repository.findById(conversationId).ifPresent(conv -> {
            conv.appendAssistant(reply);
            repository.save(conv);
        });
    }
}
