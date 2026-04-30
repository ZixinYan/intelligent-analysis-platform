package com.kuaishou.intelligentanalysisplatform.infra.stub;

import com.kuaishou.intelligentanalysisplatform.application.TaskApplicationService;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AsyncTaskStatusDTO;

public class StubTaskApplicationService implements TaskApplicationService {

    @Override
    public AsyncTaskStatusDTO getTask(String taskId) {
        if ("missing".equals(taskId)) {
            throw new BaseBusinessException(ErrorCode.ASYNC_TASK_NOT_FOUND, "task not found");
        }
        return AsyncTaskStatusDTO.builder()
                .taskId(taskId)
                .status(ExecutionStatus.RUNNING)
                .progress(20)
                .build();
    }

    @Override
    public void cancelTask(String taskId) {
        if ("missing".equals(taskId)) {
            throw new BaseBusinessException(ErrorCode.ASYNC_TASK_NOT_FOUND, "task not found");
        }
    }
}
