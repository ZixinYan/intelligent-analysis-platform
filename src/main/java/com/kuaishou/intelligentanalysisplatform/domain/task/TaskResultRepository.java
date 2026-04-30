package com.kuaishou.intelligentanalysisplatform.domain.task;

import java.util.Optional;

public interface TaskResultRepository {
    void save(TaskResult result);

    Optional<TaskResult> findById(String taskId);
}
