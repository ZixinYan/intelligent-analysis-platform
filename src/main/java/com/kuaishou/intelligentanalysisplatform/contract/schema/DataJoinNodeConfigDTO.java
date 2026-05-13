package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.contract.enums.JoinType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class DataJoinNodeConfigDTO extends BaseNodeConfigDTO {
    /** 左表来源节点 ID */
    private String leftDatasetRef;

    /** 右表来源节点 ID */
    private String rightDatasetRef;

    /** JOIN 类型：INNER / LEFT / RIGHT / FULL */
    private JoinType joinType;

    /** JOIN 条件列表（多条件 AND 关系） */
    private List<JoinCondition> on;

    /** JOIN 结果列选择，null 表示全选 */
    private List<String> selectColumns;

    /** 内存 JOIN 行数硬限制，null 时使用默认值 500_000 */
    private Integer memoryRowLimit;
}
