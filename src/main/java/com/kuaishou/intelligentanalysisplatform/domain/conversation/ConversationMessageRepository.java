package com.kuaishou.intelligentanalysisplatform.domain.conversation;

import java.util.List;

public interface ConversationMessageRepository {

    /** Append a new message row (INSERT). */
    void append(String conversationId, ConversationMessage message);

    /**
     * Insert the SYSTEM message for a conversation if none exists; update its content if it has changed.
     * This is the only UPDATE path — all other messages are append-only.
     */
    void upsertSystemMessage(String conversationId, String content, long now);

    /** Load all messages for a conversation ordered by created_at ASC. */
    List<ConversationMessage> findByConversationId(String conversationId);

    /** Delete all messages when a conversation is deleted. */
    void deleteByConversationId(String conversationId);
}
