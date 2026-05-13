package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JoinCondition {
    /** 左表的 JOIN 字段名 */
    private String leftField;
    /** 右表的 JOIN 字段名 */
    private String rightField;
}
