package com.kuaishou.intelligentanalysisplatform.application.ai.foundation.conversation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.kuaishou.intelligentanalysisplatform.application.ai.foundation.provider.AiModelProvider;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.domain.conversation.Conversation;
import com.kuaishou.intelligentanalysisplatform.domain.conversation.ConversationMessage;
import com.kuaishou.intelligentanalysisplatform.domain.conversation.ConversationMessageRepository;
import com.kuaishou.intelligentanalysisplatform.domain.conversation.ConversationRepository;
import com.kuaishou.intelligentanalysisplatform.domain.conversation.ConversationRole;
import org.springframework.stereotype.Service;

@Service
public class DefaultAiConversationService implements AiConversationService {

    private static final int HISTORY_TOKEN_BUDGET = 3000;

    private final ConversationRepository repository;
    private final ConversationMessageRepository messageRepository;

    public DefaultAiConversationService(ConversationRepository repository,
                                        ConversationMessageRepository messageRepository) {
        this.repository = repository;
        this.messageRepository = messageRepository;
    }

    @Override
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
        Conversation conversation = repository.findById(conversationId)
                .orElseThrow(() -> new BaseBusinessException(
                        ErrorCode.CONVERSATION_NOT_FOUND, "conversationId not found: " + conversationId));
        if (!sameOwner(conversation, tenantId, userId)) {
            throw new BaseBusinessException(ErrorCode.CONVERSATION_NOT_FOUND, "conversationId not found: " + conversationId);
        }
        // Populate messages from the dedicated message table
        conversation.setMessages(messageRepository.findByConversationId(conversationId));
        return conversation;
    }

    @Override
    public List<AiModelProvider.AiMessage> prepareAndSave(Conversation conversation, String systemPrompt, String userMessage) {
        long now = System.currentTimeMillis();

        // Upsert system message (INSERT first time, UPDATE if changed)
        messageRepository.upsertSystemMessage(conversation.getConversationId(), systemPrompt, now);

        // Append user message as a new row
        ConversationMessage userMsg = ConversationMessage.builder()
                .role(ConversationRole.USER)
                .content(userMessage)
                .timestamp(now)
                .estimatedTokens(userMessage != null ? userMessage.length() / 4 + 1 : 1)
                .build();
        messageRepository.append(conversation.getConversationId(), userMsg);

        // Keep in-memory list in sync for windowed history calculation
        upsertSystemPromptInMemory(conversation, systemPrompt, now);
        conversation.getMessages().add(userMsg);

        return conversation.getWindowedHistory(HISTORY_TOKEN_BUDGET).stream()
                .map(message -> new AiModelProvider.AiMessage(
                        message.getRole().name().toLowerCase(),
                        message.getContent()))
                .toList();
    }

    @Override
    public void appendAssistantReply(String conversationId, String tenantId, String userId, String reply) {
        // Lightweight ownership check — does not load the full message list
        validateOwnership(conversationId, tenantId, userId);
        long now = System.currentTimeMillis();
        messageRepository.append(conversationId, ConversationMessage.builder()
                .role(ConversationRole.ASSISTANT)
                .content(reply)
                .timestamp(now)
                .estimatedTokens(reply != null ? reply.length() / 4 + 1 : 1)
                .build());
    }

    private void validateOwnership(String conversationId, String tenantId, String userId) {
        Conversation conversation = repository.findById(conversationId)
                .orElseThrow(() -> new BaseBusinessException(
                        ErrorCode.CONVERSATION_NOT_FOUND, "conversationId not found: " + conversationId));
        if (!sameOwner(conversation, tenantId, userId)) {
            throw new BaseBusinessException(ErrorCode.CONVERSATION_NOT_FOUND, "conversationId not found: " + conversationId);
        }
    }

    private boolean sameOwner(Conversation conversation, String tenantId, String userId) {
        return java.util.Objects.equals(conversation.getTenantId(), tenantId)
                && java.util.Objects.equals(conversation.getUserId(), userId);
    }

    /** Sync the in-memory message list's SYSTEM entry to match the DB-level upsert. */
    private void upsertSystemPromptInMemory(Conversation conversation, String systemPrompt, long now) {
        if (conversation.getMessages() == null) {
            conversation.setMessages(new ArrayList<>());
        }
        ConversationMessage systemMsg = buildSystemMessage(systemPrompt, now);
        if (conversation.getMessages().isEmpty()) {
            conversation.getMessages().add(systemMsg);
            return;
        }
        ConversationMessage first = conversation.getMessages().get(0);
        if (first.getRole() == ConversationRole.SYSTEM) {
            if (!java.util.Objects.equals(first.getContent(), systemPrompt)) {
                conversation.getMessages().set(0, systemMsg);
            }
        } else {
            conversation.getMessages().add(0, systemMsg);
        }
    }

    private ConversationMessage buildSystemMessage(String systemPrompt, long now) {
        String content = systemPrompt == null ? "" : systemPrompt;
        return ConversationMessage.builder()
                .role(ConversationRole.SYSTEM)
                .content(content)
                .timestamp(now)
                .estimatedTokens(content.length() / 4 + 1)
                .build();
    }
}
