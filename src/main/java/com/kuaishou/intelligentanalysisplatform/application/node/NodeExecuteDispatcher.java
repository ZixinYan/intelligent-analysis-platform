package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NodeExecuteDispatcher {
    private static final Logger TASK_EXECUTION = LoggerFactory.getLogger("TASK_EXECUTION");

    private final NodeExecutorRegistry nodeExecutorRegistry;
    private final ObjectMapper objectMapper;

    public NodeExecuteDispatcher(NodeExecutorRegistry nodeExecutorRegistry, ObjectMapper objectMapper) {
        this.nodeExecutorRegistry = nodeExecutorRegistry;
        this.objectMapper = objectMapper;
    }

    public NodeResultDTO dispatch(WorkflowNodeDTO node, NodeExecuteContextDTO context) {
        long start = System.currentTimeMillis();
        String nodeId = node.getNodeId();
        String nodeType = node.getNodeType();
        logNodeExecution("node_dispatch_start", nodeId, nodeType, null, 0);

        try {
            NodeExecutor<? extends BaseNodeConfigDTO> executor = nodeExecutorRegistry.get(nodeType);
            ValidationResultDTO validationResult = validate(executor, node.getConfig());
            if (validationResult != null && !validationResult.isValid()) {
                long elapsed = System.currentTimeMillis() - start;
                logNodeExecution("node_dispatch_validation_failed", nodeId, nodeType,
                        ExecutionStatus.FAILED, elapsed);
                return failed(node, context, start, new BaseBusinessException(ErrorCode.VALIDATION_FAILED,
                        validationResult.getErrorMessage()));
            }
            NodeResultDTO result = execute(executor, context, node.getConfig());
            result.setNodeId(nodeId);
            result.setNodeType(nodeType);
            long elapsed = System.currentTimeMillis() - start;
            if (result.getMeta() == null) {
                result.setMeta(NodeRunMetaDTO.builder().elapsedMs(elapsed).build());
            } else if (result.getMeta().getElapsedMs() == null) {
                result.getMeta().setElapsedMs(elapsed);
            }
            logNodeExecution("node_dispatch_completed", nodeId, nodeType, result.getStatus(), elapsed);
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            logNodeExecution("node_dispatch_failed", nodeId, nodeType, ExecutionStatus.FAILED, elapsed);
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

    private void logNodeExecution(String event, String nodeId, String nodeType,
                                   ExecutionStatus status, long elapsedMs) {
        Map<String, Object> logEntry = new LinkedHashMap<>();
        logEntry.put("event", event);
        logEntry.put("nodeId", nodeId);
        logEntry.put("nodeType", nodeType);
        logEntry.put("elapsedMs", elapsedMs);
        if (status != null) {
            logEntry.put("status", status.name());
        }
        try {
            if (status == ExecutionStatus.FAILED) {
                TASK_EXECUTION.warn(objectMapper.writeValueAsString(logEntry));
            } else {
                TASK_EXECUTION.info(objectMapper.writeValueAsString(logEntry));
            }
        } catch (JsonProcessingException e) {
            TASK_EXECUTION.info("{\"event\":\"" + event + "\",\"nodeId\":\"" + nodeId + "\"}");
        }
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
