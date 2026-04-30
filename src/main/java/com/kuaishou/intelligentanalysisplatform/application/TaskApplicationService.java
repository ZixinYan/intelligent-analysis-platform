package com.kuaishou.intelligentanalysisplatform.application;

import com.kuaishou.intelligentanalysisplatform.contract.schema.AsyncTaskStatusDTO;

public interface TaskApplicationService {
    AsyncTaskStatusDTO getTask(String taskId);

    void cancelTask(String taskId);
}
