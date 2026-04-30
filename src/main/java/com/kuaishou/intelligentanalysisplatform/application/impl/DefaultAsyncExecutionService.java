package com.kuaishou.intelligentanalysisplatform.application.impl;

import java.util.UUID;

import com.kuaishou.intelligentanalysisplatform.application.QueryApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.node.NodeExecuteDispatcher;
import com.kuaishou.intelligentanalysisplatform.application.node.SqlQueryNodeExecutor;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AsyncSubmitResponseDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AsyncTaskStatusDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeDebugRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowRunRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.AsyncExecutionService;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import org.springframework.stereotype.Service;

@Service
public class DefaultAsyncExecutionService implements AsyncExecutionService {
    private static final String QUERY_TASK_PREFIX = "task-";

    private final SqlQueryNodeExecutor sqlQueryNodeExecutor;
    private final NodeExecuteDispatcher nodeExecuteDispatcher;
    private final QueryApplicationService queryApplicationService;

    public DefaultAsyncExecutionService(SqlQueryNodeExecutor sqlQueryNodeExecutor,
                                        NodeExecuteDispatcher nodeExecuteDispatcher,
                                        QueryApplicationService queryApplicationService) {
        this.sqlQueryNodeExecutor = sqlQueryNodeExecutor;
        this.nodeExecuteDispatcher = nodeExecuteDispatcher;
        this.queryApplicationService = queryApplicationService;
    }

    @Override
    public AsyncSubmitResponseDTO submitWorkflow(WorkflowRunRequestDTO request) {
        if (request == null || request.getNodes() == null || request.getNodes().isEmpty()) {
            throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "workflow nodes are required");
        }
        if (request.getNodes().size() != 1) {
            throw new BaseBusinessException(ErrorCode.NOT_IMPLEMENTED, "async workflow currently supports single sql node only");
        }
        NodeDebugRequestDTO nodeRequest = NodeDebugRequestDTO.builder()
                .workflowId(request.getWorkflowId())
                .nodeId(request.getNodes().get(0).getNodeId())
                .node(request.getNodes().get(0))
                .async(true)
                .context(request.getContext())
                .build();
        return submitNode(nodeRequest);
    }

    @Override
    public AsyncSubmitResponseDTO submitNode(NodeDebugRequestDTO request) {
        if (request == null || request.getNode() == null || request.getNode().getConfig() == null) {
            throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "node is required");
        }
        if (!"sql_query".equals(request.getNode().getNodeType())) {
            throw new BaseBusinessException(ErrorCode.UNSUPPORTED_NODE_TYPE, "async node type is not supported");
        }
        NodeExecuteContextDTO context = nodeExecuteDispatcher.buildContext(request);
        QueryRequestDTO queryRequest = sqlQueryNodeExecutor.buildQueryRequest(context,
                (com.kuaishou.intelligentanalysisplatform.contract.schema.SqlQueryNodeConfigDTO) request.getNode().getConfig());
        return queryApplicationService.runAsync(queryRequest);
    }

    @Override
    public AsyncTaskStatusDTO getTask(String taskId) {
        String queryId = toQueryId(taskId);
        QueryResultDTO result = queryApplicationService.getStatus(queryId);
        return AsyncTaskStatusDTO.builder()
                .taskId(taskId)
                .taskType("QUERY")
                .status(result.getStatus())
                .progress(toProgress(result.getStatus()))
                .dataset(result.getDataset())
                .error(result.getError())
                .createdAt(result.getExecutionMeta() == null ? null : result.getExecutionMeta().getStartedAt())
                .updatedAt(result.getExecutionMeta() == null ? null : result.getExecutionMeta().getFinishedAt())
                .build();
    }

    @Override
    public void cancelTask(String taskId) {
        queryApplicationService.cancel(toQueryId(taskId));
    }

    private String toQueryId(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "taskId is required");
        }
        if (taskId.startsWith(QUERY_TASK_PREFIX)) {
            return taskId.substring(QUERY_TASK_PREFIX.length());
        }
        return taskId;
    }

    private int toProgress(ExecutionStatus status) {
        if (status == null) {
            return 0;
        }
        return switch (status) {
            case QUEUED -> 10;
            case RUNNING -> 50;
            case SUCCEEDED, FAILED, CANCELLED -> 100;
            default -> 0;
        };
    }
}
