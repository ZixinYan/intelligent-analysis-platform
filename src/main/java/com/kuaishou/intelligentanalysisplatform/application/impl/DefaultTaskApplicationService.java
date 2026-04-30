package com.kuaishou.intelligentanalysisplatform.application.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.application.TaskApplicationService;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorInfoDTO;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AsyncTaskStatusDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.domain.query.execution.QueryExecutionRepository;
import com.kuaishou.intelligentanalysisplatform.domain.task.AsyncTask;
import com.kuaishou.intelligentanalysisplatform.domain.task.AsyncTaskRepository;
import com.kuaishou.intelligentanalysisplatform.domain.task.TaskResultRepository;
import com.kuaishou.intelligentanalysisplatform.infra.query.cancel.QueryCancellationRegistry;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class DefaultTaskApplicationService implements TaskApplicationService {
    private final AsyncTaskRepository asyncTaskRepository;
    private final TaskResultRepository taskResultRepository;
    private final QueryExecutionRepository queryExecutionRepository;
    private final QueryCancellationRegistry queryCancellationRegistry;
    private final ObjectMapper objectMapper;

    public DefaultTaskApplicationService(AsyncTaskRepository asyncTaskRepository,
                                         TaskResultRepository taskResultRepository,
                                         QueryExecutionRepository queryExecutionRepository,
                                         QueryCancellationRegistry queryCancellationRegistry,
                                         ObjectMapper objectMapper) {
        this.asyncTaskRepository = asyncTaskRepository;
        this.taskResultRepository = taskResultRepository;
        this.queryExecutionRepository = queryExecutionRepository;
        this.queryCancellationRegistry = queryCancellationRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public AsyncTaskStatusDTO getTask(String taskId) {
        AsyncTask task = asyncTaskRepository.findById(taskId)
                .orElseThrow(() -> new BaseBusinessException(ErrorCode.ASYNC_TASK_NOT_FOUND, "task not found"));
        return AsyncTaskStatusDTO.builder()
                .taskId(task.getTaskId())
                .taskType(task.getTaskType() == null ? null : task.getTaskType().name())
                .status(task.getStatus())
                .progress(resolveProgress(task.getStatus()))
                .dataset(resolveDataset(task))
                .error(resolveError(task))
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    @Override
    public void cancelTask(String taskId) {
        AsyncTask task = asyncTaskRepository.findById(taskId)
                .orElseThrow(() -> new BaseBusinessException(ErrorCode.ASYNC_TASK_NOT_FOUND, "task not found"));
        if (task.getStatus() == ExecutionStatus.CANCELLED) {
            throw new BaseBusinessException(ErrorCode.QUERY_ALREADY_CANCELLED, "task already cancelled");
        }
        if (task.getStatus() != ExecutionStatus.QUEUED && task.getStatus() != ExecutionStatus.RUNNING) {
            throw new BaseBusinessException(ErrorCode.REQUEST_CONFLICT, "task cannot be cancelled");
        }
        long now = System.currentTimeMillis();
        if (!queryCancellationRegistry.cancel(task.getRefId())) {
            queryExecutionRepository.updateStatus(task.getRefId(), ExecutionStatus.CANCELLED, now, ErrorCode.QUERY_CANCELLED.getCode(), "query cancellation requested before statement registration");
        }
        asyncTaskRepository.updateStatus(taskId, ExecutionStatus.CANCELLED, now, ErrorCode.QUERY_CANCELLED.getCode(), "query cancelled");
    }

    private DatasetDTO resolveDataset(AsyncTask task) {
        if (task.getStatus() != ExecutionStatus.SUCCEEDED) {
            return null;
        }
        return taskResultRepository.findById(task.getTaskId())
                .map(result -> {
                    try {
                        return objectMapper.readValue(result.getResultJson(), DatasetDTO.class);
                    } catch (JsonProcessingException exception) {
                        throw new BaseBusinessException(ErrorCode.INTERNAL_ERROR, "task result deserialize failed");
                    }
                })
                .orElse(null);
    }

    private ErrorInfoDTO resolveError(AsyncTask task) {
        if (task.getErrorCode() == null) {
            return null;
        }
        return ErrorInfoDTO.builder()
                .code(task.getErrorCode())
                .message(task.getErrorMessage())
                .detail(task.getErrorMessage())
                .requestId(task.getTaskId())
                .retryable(false)
                .build();
    }

    private Integer resolveProgress(ExecutionStatus status) {
        if (status == null) {
            return 0;
        }
        return switch (status) {
            case PENDING, QUEUED -> 0;
            case RUNNING -> 50;
            case SUCCEEDED, FAILED, CANCELLED -> 100;
        };
    }
}
