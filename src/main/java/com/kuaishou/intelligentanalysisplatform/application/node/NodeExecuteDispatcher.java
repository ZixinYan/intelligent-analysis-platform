package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorInfoDTO;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.schema.BaseNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeDebugRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeRunMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RawNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowNodeDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecutor;
import com.kuaishou.intelligentanalysisplatform.contract.spi.ValidationResultDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

@Component
public class NodeExecuteDispatcher {
    private static final Logger TASK_EXECUTION = LoggerFactory.getLogger("TASK_EXECUTION");

    private final NodeExecutorRegistry nodeExecutorRegistry;
    private final ObjectMapper objectMapper;
    /** 缓存每个 executor 对应的 config 泛型类型，避免重复反射 */
    private final ConcurrentHashMap<String, Class<?>> configClassCache = new ConcurrentHashMap<>();

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
            BaseNodeConfigDTO config = coerceConfig(executor, node.getConfig());
            ValidationResultDTO validationResult = validate(executor, config);
            if (validationResult != null && !validationResult.isValid()) {
                long elapsed = System.currentTimeMillis() - start;
                logNodeExecution("node_dispatch_validation_failed", nodeId, nodeType,
                        ExecutionStatus.FAILED, elapsed);
                return failed(node, context, start, new BaseBusinessException(ErrorCode.VALIDATION_FAILED,
                        validationResult.getErrorMessage()));
            }
            NodeResultDTO result = execute(executor, context, config);
            result.setNodeId(nodeId);
            result.setNodeType(nodeType);
            injectDeclaredOutputSchema(config, result);
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
                .allNodes(request.getAllNodes() != null
                        ? request.getAllNodes()
                        : List.of(request.getNode()))
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
            } else if (value instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> rawMap = (Map<String, Object>) value;
                results.put(key, normalizeMockInput(rawMap));
            } else {
                results.put(key, StandardResultDTO.builder().variables(Map.of("value", value)).build());
            }
        });
        return results;
    }

    /**
     * 将 Mock 输入 Map 规范化为 StandardResultDTO。
     *
     * <p>支持以下简化格式（方便用户在调试面板手写）：
     * <ul>
     *   <li>{@code {"rows": [...]}} — 等价于 DATASET 类型，自动包装为 dataset.rows</li>
     *   <li>{@code {"kind": "DATASET", "dataset": {"rows": [...]}}} — 标准格式，直接转换</li>
     *   <li>其他 Map — 作为 VARIABLES 变量字典</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private StandardResultDTO normalizeMockInput(Map<String, Object> raw) {
        // 简化格式：直接带 rows 字段，自动包装为 DATASET
        if (raw.containsKey("rows") && !raw.containsKey("kind") && !raw.containsKey("dataset")) {
            Object rowsVal = raw.get("rows");
            List<Map<String, Object>> rows = rowsVal instanceof List<?> list
                    ? (List<Map<String, Object>>) list
                    : List.of();
            return StandardResultDTO.builder()
                    .kind(com.kuaishou.intelligentanalysisplatform.contract.enums.ResultKind.DATASET)
                    .dataset(com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO.builder()
                            .rows(rows)
                            .build())
                    .build();
        }
        // 标准格式：尝试转为 StandardResultDTO
        try {
            StandardResultDTO converted = objectMapper.convertValue(raw, StandardResultDTO.class);
            // 转换成功但 kind 为空时，退化为 variables
            if (converted.getKind() == null && converted.getDataset() == null
                    && converted.getTable() == null && converted.getChart() == null) {
                return StandardResultDTO.builder().variables(raw).build();
            }
            return converted;
        } catch (Exception e) {
            return StandardResultDTO.builder().variables(raw).build();
        }
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

    /**
     * 当 config 是 RawNodeConfigDTO（JSON 反序列化的降级结果）时，
     * 用 ObjectMapper 将其转换为 executor 期望的具体 config 子类型。
     */
    private BaseNodeConfigDTO coerceConfig(NodeExecutor<? extends BaseNodeConfigDTO> executor,
                                           BaseNodeConfigDTO config) {
        if (!(config instanceof RawNodeConfigDTO)) {
            return config;
        }
        Class<?> targetClass = configClassCache.computeIfAbsent(executor.supportType(), key -> {
            ResolvableType resolvableType = ResolvableType.forClass(executor.getClass())
                    .as(NodeExecutor.class);
            Class<?> resolved = resolvableType.getGeneric(0).resolve();
            return resolved != null ? resolved : BaseNodeConfigDTO.class;
        });
        if (targetClass == BaseNodeConfigDTO.class || targetClass == RawNodeConfigDTO.class) {
            return config;
        }
        return (BaseNodeConfigDTO) objectMapper.convertValue(config, targetClass);
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

    /**
     * 若节点 config 声明了输出字段（outputs），将其注入到执行结果的 dataset.schema，
     * 使下游节点无需推断即可获取用户定义的字段名与类型。
     */
    private void injectDeclaredOutputSchema(BaseNodeConfigDTO config, NodeResultDTO result) {
        if (config.getOutputs() == null || config.getOutputs().isEmpty()) {
            return;
        }
        if (result.getResult() == null || result.getResult().getDataset() == null) {
            return;
        }
        List<FieldSchemaDTO> fields = config.getOutputs().stream()
                .filter(o -> o.getName() != null && !o.getName().isBlank())
                .map(o -> FieldSchemaDTO.builder()
                        .fieldId(o.getName())
                        .name(o.getName())
                        .displayName(o.getLabel() != null && !o.getLabel().isBlank()
                                ? o.getLabel() : o.getName())
                        .valueType(o.getValueType())
                        .nullable(true)
                        .build())
                .toList();
        if (!fields.isEmpty()) {
            result.getResult().getDataset().setSchema(
                    DatasetSchemaDTO.builder().fields(fields).build());
        }
    }
}
