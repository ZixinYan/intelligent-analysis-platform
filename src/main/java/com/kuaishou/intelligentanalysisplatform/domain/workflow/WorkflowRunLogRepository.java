package com.kuaishou.intelligentanalysisplatform.domain.workflow;

import java.util.List;
import java.util.Optional;

public interface WorkflowRunLogRepository {

    void insert(WorkflowRunLog runLog);

    void complete(String runId, String status, long elapsedMs, Long finishedAt, String nodeTraceJson);

    Optional<WorkflowRunLog> findByRunId(String runId);

    List<WorkflowRunLog> findByWorkflowId(String workflowId, String status, int offset, int limit);

    long countByWorkflowId(String workflowId, String status);
}
