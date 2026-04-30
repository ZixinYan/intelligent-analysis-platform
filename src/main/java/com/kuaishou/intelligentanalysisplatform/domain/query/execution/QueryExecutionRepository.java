package com.kuaishou.intelligentanalysisplatform.domain.query.execution;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;

import java.util.Optional;

public interface QueryExecutionRepository {
    void save(QueryExecution execution);

    void updateStatus(String queryId, ExecutionStatus status, Long finishedAt, String errorCode, String errorMessage);

    void updateResult(String queryId, ExecutionStatus status, Long finishedAt, Long elapsedMs,
                      boolean cached, boolean truncated, int rowCount);

    Optional<QueryExecution> findById(String queryId);
}
