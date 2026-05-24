package com.kuaishou.intelligentanalysisplatform.application.ai.agent.task;

import org.springframework.stereotype.Component;

@Component
public class NoopAgentTaskCancellationHandler implements AgentTaskCancellationHandler {
    @Override
    public boolean cancel(String taskId, String refId) {
        return false;
    }
}
