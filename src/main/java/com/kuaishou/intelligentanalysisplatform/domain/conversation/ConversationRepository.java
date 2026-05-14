package com.kuaishou.intelligentanalysisplatform.domain.conversation;

import java.util.Optional;

public interface ConversationRepository {
    Conversation save(Conversation conversation);
    Optional<Conversation> findById(String conversationId);
    void deleteById(String conversationId);
}
