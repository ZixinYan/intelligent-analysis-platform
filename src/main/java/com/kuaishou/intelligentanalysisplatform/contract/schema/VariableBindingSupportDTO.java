package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VariableBindingSupportDTO {
    private Boolean enabled;
    private Boolean allowLiteral;
    private String bindingPathHint;
}
