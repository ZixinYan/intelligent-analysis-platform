package com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.sql;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.application.ai.foundation.ConversationalStreamExecutor;
import com.kuaishou.intelligentanalysisplatform.application.ai.foundation.provider.AiModelProvider;
import com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.AiStreamOutputHandler;
import com.kuaishou.intelligentanalysisplatform.domain.conversation.Conversation;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AiSqlStreamExecutorTest {

    @Test
    void shouldDelegateToConversationalStreamExecutorWithCorrectArgs() {
        ConversationalStreamExecutor streamExecutor = mock(ConversationalStreamExecutor.class);
        AiSqlStreamExecutor executor = new AiSqlStreamExecutor(streamExecutor);

        Conversation conversation = Conversation.builder().conversationId("conv-1").build();
        AiSqlPromptContext promptContext = new AiSqlPromptContext(
                conversation,
                "system prompt",
                "user prompt",
                List.of(new AiModelProvider.AiMessage("user", "hello")));
        AiStreamOutputHandler handler = mock(AiStreamOutputHandler.class);

        executor.stream(promptContext, "tenant-a", "user-a", handler);

        verify(streamExecutor).streamAndSave(
                eq(new AiModelProvider.AiChatRequest("system prompt", "user prompt",
                        List.of(new AiModelProvider.AiMessage("user", "hello")))),
                eq(conversation),
                eq("tenant-a"),
                eq("user-a"),
                eq(handler));
    }
}
