package com.kuaishou.intelligentanalysisplatform.contract.schema;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ValueType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NodeOutputDefinitionDTO {
    private String name;
    private String label;
    private ValueType valueType;
    private String description;
}
