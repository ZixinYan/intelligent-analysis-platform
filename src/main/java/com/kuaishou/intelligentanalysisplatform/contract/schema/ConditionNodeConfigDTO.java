package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 条件分支节点配置。
 * 从上游节点的结果中取出指定字段值，与 compareValue 按 operator 比较，
 * 输出 VARIABLES { "_branch": "true" | "false" }，激活对应出边。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ConditionNodeConfigDTO extends BaseNodeConfigDTO {
    /** 引用哪个上游节点的结果（对应 VariableRefDTO.sourceNodeId） */
    private String sourceNodeId;
    /** 字段路径，支持嵌套：dataset.rows.0.amount（与 VariableRefDTO.path 语义一致） */
    private String fieldPath;
    /** 比较运算符，取 ConditionOperator 枚举的 name() */
    private String operator;
    /** 字面量比较值；IS_EMPTY/IS_NOT_EMPTY 时忽略 */
    private Object compareValue;
    /** 前端画布标注文字（展示用） */
    private String trueBranchLabel;
    private String falseBranchLabel;
}
