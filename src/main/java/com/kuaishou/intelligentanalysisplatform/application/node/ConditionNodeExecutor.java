package com.kuaishou.intelligentanalysisplatform.application.node;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.application.NodeMetadataApplicationService;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ConditionOperator;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ResultKind;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ConditionNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.VariableRefDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecutor;
import com.kuaishou.intelligentanalysisplatform.contract.spi.ValidationResultDTO;
import org.springframework.stereotype.Component;

/**
 * 条件分支节点执行器（参考 dify if_else_node 逻辑）。
 * 从上游结果中取值，按 operator 比较，输出 {"_branch": "true"|"false"}。
 * WorkflowDagExecutor 读取 _branch 后激活对应出边、跳过另一分支。
 */
@Component
public class ConditionNodeExecutor implements NodeExecutor<ConditionNodeConfigDTO> {

    private static final String BRANCH_TRUE  = "true";
    private static final String BRANCH_FALSE = "false";

    private final NodeMetadataApplicationService nodeMetadataApplicationService;
    private final RuntimeBindingResolver bindingResolver;

    public ConditionNodeExecutor(NodeMetadataApplicationService nodeMetadataApplicationService,
                                 RuntimeBindingResolver bindingResolver) {
        this.nodeMetadataApplicationService = nodeMetadataApplicationService;
        this.bindingResolver = bindingResolver;
    }

    @Override
    public String supportType() { return "condition"; }

    @Override
    public NodeMetaDTO metadata() {
        return nodeMetadataApplicationService.getNodeDefinition(supportType());
    }

    @Override
    public ValidationResultDTO validate(ConditionNodeConfigDTO config) {
        if (config == null || config.getSourceNodeId() == null || config.getSourceNodeId().isBlank()) {
            return ValidationResultDTO.builder().valid(false).errorMessage("condition 节点必须配置 sourceNodeId").build();
        }
        if (config.getOperator() == null || config.getOperator().isBlank()) {
            return ValidationResultDTO.builder().valid(false).errorMessage("condition 节点必须配置 operator").build();
        }
        return ValidationResultDTO.builder().valid(true).build();
    }

    @Override
    public NodeResultDTO execute(NodeExecuteContextDTO context, ConditionNodeConfigDTO config) {
        // 1. 解析上游字段值
        List<String> pathSegments = (config.getFieldPath() != null && !config.getFieldPath().isBlank())
                ? Arrays.asList(config.getFieldPath().split("\\."))
                : List.of();
        VariableRefDTO varRef = VariableRefDTO.builder()
                .sourceNodeId(config.getSourceNodeId())
                .path(pathSegments)
                .build();

        Object fieldValue = bindingResolver.resolveVariable(varRef, context.getUpstreamResults());

        // 2. 根据 operator 计算分支结果
        boolean result = evaluate(fieldValue, config.getOperator(), config.getCompareValue(),
                context.getNodeId(), config.getSourceNodeId(), config.getFieldPath());

        // 3. 返回 VARIABLES { "_branch": "true"|"false" }
        String branch = result ? BRANCH_TRUE : BRANCH_FALSE;
        StandardResultDTO standardResult = StandardResultDTO.builder()
                .kind(ResultKind.VARIABLES)
                .variables(Map.of("_branch", branch))
                .build();

        return NodeResultDTO.builder()
                .nodeId(context.getNodeId())
                .nodeType(supportType())
                .status(ExecutionStatus.SUCCEEDED)
                .result(standardResult)
                .build();
    }

    private boolean evaluate(Object value, String operatorName, Object compareValue,
                             String nodeId, String sourceNodeId, String fieldPath) {
        ConditionOperator op;
        try {
            op = ConditionOperator.valueOf(operatorName);
        } catch (IllegalArgumentException e) {
            throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT,
                    "condition 节点不支持的运算符: " + operatorName);
        }

        return switch (op) {
            case IS_EMPTY     -> isEmpty(value);
            case IS_NOT_EMPTY -> !isEmpty(value);
            case EQ           -> compareEquals(value, compareValue);
            case NEQ          -> !compareEquals(value, compareValue);
            case GT           -> compareNumeric(value, compareValue, nodeId) > 0;
            case GTE          -> compareNumeric(value, compareValue, nodeId) >= 0;
            case LT           -> compareNumeric(value, compareValue, nodeId) < 0;
            case LTE          -> compareNumeric(value, compareValue, nodeId) <= 0;
            case CONTAINS     -> containsValue(value, compareValue);
            case NOT_CONTAINS -> !containsValue(value, compareValue);
            case IN           -> inCollection(value, compareValue);
            case NOT_IN       -> !inCollection(value, compareValue);
        };
    }

    private boolean isEmpty(Object value) {
        if (value == null) return true;
        if (value instanceof String s) return s.isBlank();
        if (value instanceof Collection<?> c) return c.isEmpty();
        return false;
    }

    private boolean compareEquals(Object value, Object compareValue) {
        if (value == null && compareValue == null) return true;
        if (value == null || compareValue == null) return false;
        // 尝试数值比较（参考 dify：将两侧都转为 BigDecimal）
        try {
            BigDecimal a = toBigDecimal(value);
            BigDecimal b = toBigDecimal(compareValue);
            if (a != null && b != null) return a.compareTo(b) == 0;
        } catch (Exception ignored) { }
        return value.toString().equals(compareValue.toString());
    }

    private int compareNumeric(Object value, Object compareValue, String nodeId) {
        BigDecimal a = toBigDecimal(value);
        BigDecimal b = toBigDecimal(compareValue);
        if (a == null || b == null) {
            throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT,
                    "condition 节点 [" + nodeId + "] 数值比较失败：值不可转换为数字（value=" + value + ", compare=" + compareValue + "）");
        }
        return a.compareTo(b);
    }

    private boolean containsValue(Object value, Object compareValue) {
        if (value == null || compareValue == null) return false;
        if (value instanceof Collection<?> col) {
            String cmp = compareValue.toString();
            return col.stream().anyMatch(item -> item != null && item.toString().equals(cmp));
        }
        return value.toString().contains(compareValue.toString());
    }

    @SuppressWarnings("unchecked")
    private boolean inCollection(Object value, Object compareValue) {
        if (compareValue instanceof Collection<?> col) {
            String v = value == null ? "" : value.toString();
            return col.stream().anyMatch(item -> item != null && item.toString().equals(v));
        }
        return false;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        try {
            return new BigDecimal(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
