package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 错误恢复节点配置。
 * 监听 guardedNodeId 节点的执行结果，在 FAILED 时按策略决策：
 * - SKIP：将被保护节点及其下游标记为 SKIPPED，整体工作流继续
 * - DEFAULT_VALUE：用 defaultValue 替代被保护节点的输出，工作流 SUCCEEDED
 * - FAIL：保持原始失败状态向下传播
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ErrorHandlerNodeConfigDTO extends BaseNodeConfigDTO {
    /** 被保护节点的 nodeId */
    private String guardedNodeId;
    /** 最大重试次数，默认 0（不重试） */
    private int maxRetries;
    /** 重试间隔（毫秒） */
    private long retryDelayMs;
    /** 失败兜底行为：SKIP | DEFAULT_VALUE | FAIL */
    private String fallbackBehavior;
    /** fallbackBehavior = DEFAULT_VALUE 时作为替代输出值 */
    private Object defaultValue;
}
