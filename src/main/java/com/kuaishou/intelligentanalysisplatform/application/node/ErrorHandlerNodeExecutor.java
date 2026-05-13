package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.application.NodeMetadataApplicationService;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorInfoDTO;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ResultKind;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ErrorHandlerNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowNodeDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecutor;
import com.kuaishou.intelligentanalysisplatform.contract.spi.ValidationResultDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 错误恢复节点执行器。
 * 在被保护节点失败时触发，执行重试 + fallback 决策。
 * 注意：此节点在 WorkflowDagExecutor 中享有特殊待遇——
 * 上游 FAILED 不会触发 SKIPPED 传播，而是直接执行本 executor。
 */
@Component
public class ErrorHandlerNodeExecutor implements NodeExecutor<ErrorHandlerNodeConfigDTO> {

    private static final String FALLBACK_SKIP          = "SKIP";
    private static final String FALLBACK_DEFAULT_VALUE = "DEFAULT_VALUE";
    private static final String FALLBACK_FAIL          = "FAIL";

    private final NodeMetadataApplicationService nodeMetadataApplicationService;

    @Lazy
    @Autowired
    private NodeExecuteDispatcher nodeExecuteDispatcher;

    public ErrorHandlerNodeExecutor(NodeMetadataApplicationService nodeMetadataApplicationService) {
        this.nodeMetadataApplicationService = nodeMetadataApplicationService;
    }

    @Override
    public String supportType() { return "error_handler"; }

    @Override
    public NodeMetaDTO metadata() {
        return nodeMetadataApplicationService.getNodeDefinition(supportType());
    }

    @Override
    public ValidationResultDTO validate(ErrorHandlerNodeConfigDTO config) {
        if (config == null || config.getGuardedNodeId() == null || config.getGuardedNodeId().isBlank()) {
            return ValidationResultDTO.builder().valid(false).errorMessage("error_handler 必须配置 guardedNodeId").build();
        }
        return ValidationResultDTO.builder().valid(true).build();
    }

    @Override
    public NodeResultDTO execute(NodeExecuteContextDTO context, ErrorHandlerNodeConfigDTO config) {
        String guardedNodeId = config.getGuardedNodeId();

        // 找到被保护节点的定义（用于 retry）
        WorkflowNodeDTO guardedNode = findGuardedNode(context, guardedNodeId);

        // 尝试重试
        NodeResultDTO lastResult = retryGuardedNode(guardedNode, context, config);

        if (lastResult != null && lastResult.getStatus() == ExecutionStatus.SUCCEEDED) {
            // 重试成功，将被保护节点的结果透传给下游
            return NodeResultDTO.builder()
                    .nodeId(context.getNodeId())
                    .nodeType(supportType())
                    .status(ExecutionStatus.SUCCEEDED)
                    .result(lastResult.getResult())
                    .build();
        }

        // 所有重试均失败，执行 fallback
        return applyFallback(context, config, lastResult);
    }

    private WorkflowNodeDTO findGuardedNode(NodeExecuteContextDTO context, String guardedNodeId) {
        if (context.getAllNodes() == null) return null;
        return context.getAllNodes().stream()
                .filter(n -> guardedNodeId.equals(n.getNodeId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 对被保护节点最多重试 maxRetries 次。
     * 返回最后一次执行结果（可能成功或失败）；若 guardedNode 为 null 则返回 null。
     */
    private NodeResultDTO retryGuardedNode(WorkflowNodeDTO guardedNode,
                                            NodeExecuteContextDTO context,
                                            ErrorHandlerNodeConfigDTO config) {
        if (guardedNode == null || config.getMaxRetries() <= 0) {
            return null;
        }

        NodeExecuteContextDTO retryContext = NodeExecuteContextDTO.builder()
                .workflowId(context.getWorkflowId())
                .runId(context.getRunId())
                .nodeId(guardedNode.getNodeId())
                .upstreamResults(context.getUpstreamResults())
                .requestContext(context.getRequestContext())
                .allNodes(context.getAllNodes())
                .build();

        NodeResultDTO lastResult = null;
        for (int attempt = 0; attempt < config.getMaxRetries(); attempt++) {
            if (attempt > 0 && config.getRetryDelayMs() > 0) {
                try {
                    Thread.sleep(config.getRetryDelayMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            lastResult = nodeExecuteDispatcher.dispatch(guardedNode, retryContext);
            if (lastResult.getStatus() == ExecutionStatus.SUCCEEDED) {
                return lastResult;
            }
        }
        return lastResult;
    }

    private NodeResultDTO applyFallback(NodeExecuteContextDTO context,
                                         ErrorHandlerNodeConfigDTO config,
                                         NodeResultDTO lastRetryResult) {
        String behavior = config.getFallbackBehavior() != null ? config.getFallbackBehavior().toUpperCase() : FALLBACK_FAIL;

        return switch (behavior) {
            case FALLBACK_SKIP -> NodeResultDTO.builder()
                    .nodeId(context.getNodeId())
                    .nodeType(supportType())
                    .status(ExecutionStatus.SKIPPED)
                    .build();

            case FALLBACK_DEFAULT_VALUE -> {
                StandardResultDTO defaultResult = StandardResultDTO.builder()
                        .kind(ResultKind.VARIABLES)
                        .variables(Map.of("value", config.getDefaultValue() != null ? config.getDefaultValue() : ""))
                        .build();
                yield NodeResultDTO.builder()
                        .nodeId(context.getNodeId())
                        .nodeType(supportType())
                        .status(ExecutionStatus.SUCCEEDED)
                        .result(defaultResult)
                        .build();
            }

            default -> NodeResultDTO.builder()
                    .nodeId(context.getNodeId())
                    .nodeType(supportType())
                    .status(ExecutionStatus.FAILED)
                    .error(ErrorInfoDTO.builder()
                            .code(ErrorCode.INTERNAL_ERROR.getCode())
                            .message("被保护节点 [" + config.getGuardedNodeId() + "] 重试后仍失败，ErrorHandler fallback=FAIL")
                            .retryable(false)
                            .build())
                    .build();
        };
    }
}
