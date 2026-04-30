package com.kuaishou.intelligentanalysisplatform.application.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeDebugRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowNodeDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowRunRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowRunResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.SyncExecutionService;
import com.kuaishou.intelligentanalysisplatform.application.node.NodeExecuteDispatcher;
import org.springframework.stereotype.Service;

@Service
public class DefaultSyncExecutionService implements SyncExecutionService {
    private final NodeExecuteDispatcher nodeExecuteDispatcher;

    public DefaultSyncExecutionService(NodeExecuteDispatcher nodeExecuteDispatcher) {
        this.nodeExecuteDispatcher = nodeExecuteDispatcher;
    }

    @Override
    public NodeResultDTO runNode(NodeDebugRequestDTO request) {
        validateNodeRequest(request);
        NodeExecuteContextDTO context = nodeExecuteDispatcher.buildContext(request);
        return nodeExecuteDispatcher.dispatch(request.getNode(), context);
    }

    @Override
    public WorkflowRunResultDTO runWorkflow(WorkflowRunRequestDTO request) {
        if (request == null || request.getNodes() == null || request.getNodes().isEmpty()) {
            throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "workflow nodes are required");
        }
        String runId = UUID.randomUUID().toString();
        Map<String, StandardResultDTO> upstreamResults = new LinkedHashMap<>();
        List<NodeResultDTO> nodeResults = new ArrayList<>();
        ExecutionStatus workflowStatus = ExecutionStatus.SUCCEEDED;
        StandardResultDTO finalResult = null;
        for (WorkflowNodeDTO node : request.getNodes()) {
            NodeExecuteContextDTO context = NodeExecuteContextDTO.builder()
                    .workflowId(request.getWorkflowId())
                    .runId(runId)
                    .nodeId(node.getNodeId())
                    .upstreamResults(upstreamResults)
                    .requestContext(request.getContext())
                    .build();
            NodeResultDTO result = nodeExecuteDispatcher.dispatch(node, context);
            nodeResults.add(result);
            if (result.getResult() != null) {
                upstreamResults.put(node.getNodeId(), result.getResult());
                finalResult = result.getResult();
            }
            if (result.getStatus() != ExecutionStatus.SUCCEEDED && result.getStatus() != ExecutionStatus.QUEUED) {
                workflowStatus = result.getStatus();
                break;
            }
            if (result.getStatus() == ExecutionStatus.QUEUED) {
                workflowStatus = ExecutionStatus.QUEUED;
            }
        }
        return WorkflowRunResultDTO.builder()
                .workflowId(request.getWorkflowId())
                .status(workflowStatus)
                .nodeResults(nodeResults)
                .finalResult(finalResult)
                .build();
    }

    private void validateNodeRequest(NodeDebugRequestDTO request) {
        if (request == null || request.getNode() == null) {
            throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "node is required");
        }
    }
}
