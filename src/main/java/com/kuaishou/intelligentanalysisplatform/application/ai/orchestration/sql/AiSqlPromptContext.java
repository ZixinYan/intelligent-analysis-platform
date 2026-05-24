package com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.sql;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.application.ai.foundation.provider.AiModelProvider;
import com.kuaishou.intelligentanalysisplatform.domain.conversation.Conversation;

public record AiSqlPromptContext(
        Conversation conversation,
        String systemPrompt,
        String userMessage,
        List<AiModelProvider.AiMessage> history
) {
}
