package com.kuaishou.intelligentanalysisplatform.application.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.application.TaskApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.ai.agent.task.AgentTaskCancellationHandler;
import com.kuaishou.intelligentanalysisplatform.application.ai.agent.task.AgentTaskResultDTO;
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
import com.kuaishou.intelligentanalysisplatform.domain.task.TaskType;
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
    private final AgentTaskCancellationHandler agentTaskCancellationHandler;
    private final ObjectMapper objectMapper;

    public DefaultTaskApplicationService(AsyncTaskRepository asyncTaskRepository,
                                         TaskResultRepository taskResultRepository,
                                         QueryExecutionRepository queryExecutionRepository,
                                         QueryCancellationRegistry queryCancellationRegistry,
                                         AgentTaskCancellationHandler agentTaskCancellationHandler,
                                         ObjectMapper objectMapper) {
        this.asyncTaskRepository = asyncTaskRepository;
        this.taskResultRepository = taskResultRepository;
        this.queryExecutionRepository = queryExecutionRepository;
        this.queryCancellationRegistry = queryCancellationRegistry;
        this.agentTaskCancellationHandler = agentTaskCancellationHandler;
        this.objectMapper = objectMapper;
    }

    @Override
    public AsyncTaskStatusDTO getTask(String taskId) {
        AsyncTask task = asyncTaskRepository.findById(taskId)
                .orElseThrow(() -> new BaseBusinessException(ErrorCode.ASYNC_TASK_NOT_FOUND, "task not found"));
        AgentTaskResultDTO agentTaskResult = resolveAgentTaskResult(task);
        return AsyncTaskStatusDTO.builder()
                .taskId(task.getTaskId())
                .agentTaskId(resolveAgentTaskId(task, agentTaskResult))
                .taskType(task.getTaskType() == null ? null : task.getTaskType().name())
                .status(task.getStatus())
                .progress(resolveProgress(task.getStatus()))
                .dataset(resolveDataset(task))
                .clarification(agentTaskResult == null ? null : agentTaskResult.getClarification())
                .trace(agentTaskResult == null ? null : agentTaskResult.getTrace())
                .confidence(agentTaskResult == null ? null : agentTaskResult.getConfidence())
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
        if (!task.isCancellable()) {
            throw new BaseBusinessException(ErrorCode.REQUEST_CONFLICT, "task cannot be cancelled");
        }
        if (task.getTaskType() == TaskType.AGENT) {
            cancelAgentTask(task);
            return;
        }
        cancelQueryTask(task);
    }

    private void cancelQueryTask(AsyncTask task) {
        long now = System.currentTimeMillis();
        if (!queryCancellationRegistry.cancel(task.getRefId())) {
            queryExecutionRepository.updateStatus(task.getRefId(), ExecutionStatus.CANCELLED, now, ErrorCode.QUERY_CANCELLED.getCode(), "query cancellation requested before statement registration");
        }
        asyncTaskRepository.updateStatus(task.getTaskId(), ExecutionStatus.CANCELLED, now, ErrorCode.QUERY_CANCELLED.getCode(), "query cancelled");
    }

    private void cancelAgentTask(AsyncTask task) {
        long now = System.currentTimeMillis();
        boolean cancelled = agentTaskCancellationHandler.cancel(task.getTaskId(), task.getRefId());
        String message = cancelled ? "agent task cancelled" : "agent task cancellation requested";
        asyncTaskRepository.updateStatus(task.getTaskId(), ExecutionStatus.CANCELLED, now, "AGENT_TASK_CANCELLED", message);
    }

    private DatasetDTO resolveDataset(AsyncTask task) {
        if (task.getTaskType() != TaskType.QUERY || task.getStatus() != ExecutionStatus.SUCCEEDED) {
            return null;
        }
        return taskResultRepository.findById(task.getTaskId())
                .map(result -> readTaskResult(result.getResultJson(), DatasetDTO.class))
                .orElse(null);
    }

    private AgentTaskResultDTO resolveAgentTaskResult(AsyncTask task) {
        if (task.getTaskType() != TaskType.AGENT) {
            return null;
        }
        return taskResultRepository.findById(task.getTaskId())
                .map(result -> readTaskResult(result.getResultJson(), AgentTaskResultDTO.class))
                .orElse(null);
    }

    private String resolveAgentTaskId(AsyncTask task, AgentTaskResultDTO agentTaskResult) {
        if (task.getTaskType() != TaskType.AGENT) {
            return null;
        }
        if (agentTaskResult != null && agentTaskResult.getAgentTaskId() != null && !agentTaskResult.getAgentTaskId().isBlank()) {
            return agentTaskResult.getAgentTaskId();
        }
        return task.getTaskId();
    }

    private <T> T readTaskResult(String resultJson, Class<T> resultType) {
        try {
            return objectMapper.readValue(resultJson, resultType);
        } catch (JsonProcessingException exception) {
            throw new BaseBusinessException(ErrorCode.INTERNAL_ERROR, "task result deserialize failed");
        }
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
            case SKIPPED -> 100;
        };
    }
}
