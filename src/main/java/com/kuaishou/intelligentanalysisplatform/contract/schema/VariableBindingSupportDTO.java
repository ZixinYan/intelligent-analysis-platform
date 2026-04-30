package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VariableBindingSupportDTO {
    private Boolean enabled;
    private Boolean allowLiteral;
    private String bindingPathHint;
}
