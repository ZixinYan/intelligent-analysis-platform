package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorInfoDTO;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.schema.BaseNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeDebugRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeRunMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowNodeDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecutor;
import com.kuaishou.intelligentanalysisplatform.contract.spi.ValidationResultDTO;
import org.springframework.stereotype.Component;

@Component
public class NodeExecuteDispatcher {
    private final NodeExecutorRegistry nodeExecutorRegistry;

    public NodeExecuteDispatcher(NodeExecutorRegistry nodeExecutorRegistry) {
        this.nodeExecutorRegistry = nodeExecutorRegistry;
    }

    public NodeResultDTO dispatch(WorkflowNodeDTO node, NodeExecuteContextDTO context) {
        long start = System.currentTimeMillis();
        try {
            NodeExecutor<? extends BaseNodeConfigDTO> executor = nodeExecutorRegistry.get(node.getNodeType());
            ValidationResultDTO validationResult = validate(executor, node.getConfig());
            if (validationResult != null && !validationResult.isValid()) {
                return failed(node, context, start, new BaseBusinessException(ErrorCode.VALIDATION_FAILED, validationResult.getErrorMessage()));
            }
            NodeResultDTO result = execute(executor, context, node.getConfig());
            result.setNodeId(node.getNodeId());
            result.setNodeType(node.getNodeType());
            if (result.getMeta() == null) {
                result.setMeta(NodeRunMetaDTO.builder().elapsedMs(System.currentTimeMillis() - start).build());
            } else if (result.getMeta().getElapsedMs() == null) {
                result.getMeta().setElapsedMs(System.currentTimeMillis() - start);
            }
            return result;
        } catch (Exception e) {
            return failed(node, context, start, e);
        }
    }

    public NodeExecuteContextDTO buildContext(NodeDebugRequestDTO request) {
        return NodeExecuteContextDTO.builder()
                .workflowId(request.getWorkflowId())
                .runId(UUID.randomUUID().toString())
                .nodeId(request.getNodeId())
                .upstreamResults(toUpstreamResults(request.getUpstreamMockInputs()))
                .requestContext(request.getContext())
                .build();
    }

    private Map<String, StandardResultDTO> toUpstreamResults(Map<String, Object> inputs) {
        Map<String, StandardResultDTO> results = new LinkedHashMap<>();
        if (inputs == null || inputs.isEmpty()) {
            return results;
        }
        inputs.forEach((key, value) -> {
            if (value instanceof StandardResultDTO standardResult) {
                results.put(key, standardResult);
            } else if (value instanceof Map<?, ?> map) {
                results.put(key, StandardResultDTO.builder().variables((Map<String, Object>) map).build());
            } else {
                results.put(key, StandardResultDTO.builder().variables(Map.of("value", value)).build());
            }
        });
        return results;
    }

    @SuppressWarnings("unchecked")
    private ValidationResultDTO validate(NodeExecutor<? extends BaseNodeConfigDTO> executor, BaseNodeConfigDTO config) {
        return ((NodeExecutor<BaseNodeConfigDTO>) executor).validate(config);
    }

    @SuppressWarnings("unchecked")
    private NodeResultDTO execute(NodeExecutor<? extends BaseNodeConfigDTO> executor,
                                  NodeExecuteContextDTO context,
                                  BaseNodeConfigDTO config) {
        return ((NodeExecutor<BaseNodeConfigDTO>) executor).execute(context, config);
    }

    private NodeResultDTO failed(WorkflowNodeDTO node, NodeExecuteContextDTO context, long start, Exception e) {
        ErrorInfoDTO errorInfo = toErrorInfo(context == null ? null : context.getRunId(), node == null ? null : node.getNodeId(), e);
        return NodeResultDTO.builder()
                .nodeId(node == null ? null : node.getNodeId())
                .nodeType(node == null ? null : node.getNodeType())
                .status(ExecutionStatus.FAILED)
                .error(errorInfo)
                .meta(NodeRunMetaDTO.builder().elapsedMs(System.currentTimeMillis() - start).build())
                .build();
    }

    private ErrorInfoDTO toErrorInfo(String requestId, String nodeId, Exception exception) {
        if (exception instanceof BaseBusinessException businessException) {
            return ErrorInfoDTO.builder()
                    .code(businessException.getErrorCode().getCode())
                    .message(businessException.getMessage())
                    .detail(businessException.getDetail())
                    .requestId(requestId)
                    .nodeId(nodeId)
                    .retryable(businessException.isRetryable())
                    .build();
        }
        return ErrorInfoDTO.builder()
                .code(ErrorCode.INTERNAL_ERROR.getCode())
                .message(exception.getMessage())
                .detail(exception.getClass().getSimpleName())
                .requestId(requestId)
                .nodeId(nodeId)
                .retryable(false)
                .build();
    }
}
