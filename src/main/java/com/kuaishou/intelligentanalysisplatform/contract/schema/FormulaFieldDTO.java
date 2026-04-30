package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FormulaFieldDTO {
    private String alias;
    private String expression;
    private String format;
}
