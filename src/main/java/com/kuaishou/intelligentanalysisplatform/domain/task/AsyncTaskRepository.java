package com.kuaishou.intelligentanalysisplatform.domain.task;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;

import java.util.Optional;

public interface AsyncTaskRepository {
    void save(AsyncTask task);

    void updateStatus(String taskId, ExecutionStatus status, Long updatedAt, String errorCode, String errorMessage);

    Optional<AsyncTask> findById(String taskId);
}
