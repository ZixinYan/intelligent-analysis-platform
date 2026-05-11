package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class MaskingNodeConfigDTO extends BaseNodeConfigDTO {
    /** 上游 Dataset 引用 */
    private VariableRefDTO datasetRef;
    /** 脱敏规则列表 */
    private List<MaskingRuleDTO> rules;
}
