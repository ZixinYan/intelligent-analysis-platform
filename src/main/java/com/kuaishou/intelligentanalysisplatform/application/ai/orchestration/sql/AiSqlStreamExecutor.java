package com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.sql;

import com.kuaishou.intelligentanalysisplatform.application.ai.foundation.ConversationalStreamExecutor;
import com.kuaishou.intelligentanalysisplatform.application.ai.foundation.provider.AiModelProvider;
import com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.AiStreamOutputHandler;
import org.springframework.stereotype.Component;

@Component
public class AiSqlStreamExecutor {

    private final ConversationalStreamExecutor streamExecutor;

    public AiSqlStreamExecutor(ConversationalStreamExecutor streamExecutor) {
        this.streamExecutor = streamExecutor;
    }

    public void stream(AiSqlPromptContext promptContext, String tenantId, String userId, AiStreamOutputHandler handler) {
        streamExecutor.streamAndSave(
                new AiModelProvider.AiChatRequest(
                        promptContext.systemPrompt(),
                        promptContext.userMessage(),
                        promptContext.history()),
                promptContext.conversation(),
                tenantId, userId,
                handler);
    }
}
