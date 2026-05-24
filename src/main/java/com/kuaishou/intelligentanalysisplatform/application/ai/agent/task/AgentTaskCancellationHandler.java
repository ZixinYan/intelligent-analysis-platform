package com.kuaishou.intelligentanalysisplatform.application.ai.agent.task;

public interface AgentTaskCancellationHandler {
    boolean cancel(String taskId, String refId);
}
