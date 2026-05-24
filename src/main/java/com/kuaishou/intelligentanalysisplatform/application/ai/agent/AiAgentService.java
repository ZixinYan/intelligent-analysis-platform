package com.kuaishou.intelligentanalysisplatform.application.ai.agent;

import com.kuaishou.intelligentanalysisplatform.application.ai.foundation.ConversationalStreamExecutor;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiChatRequestDTO;

public interface AiAgentService {

    AiAgentTask createPlaceholderTask(String capability);

    void streamChat(AiChatRequestDTO request, RequestContextDTO context, ChatStreamHandler handler);

    interface ChatStreamHandler extends ConversationalStreamExecutor.StreamHandler {
    }
}
