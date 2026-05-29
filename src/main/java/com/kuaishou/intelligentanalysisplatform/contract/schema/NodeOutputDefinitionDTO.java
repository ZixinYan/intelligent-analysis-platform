package com.kuaishou.intelligentanalysisplatform.contract.schema;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ValueType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeOutputDefinitionDTO {
    /** 原始列名（节点计算产出的实际 key），用于 rows 重命名映射。为 null 时向后兼容。 */
    private String source;
    /** 用户自定义列名（重命名后的目标 key） */
    private String name;
    private String label;
    private ValueType valueType;
    private String description;
}
