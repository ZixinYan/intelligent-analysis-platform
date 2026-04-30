package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ValidationRuleDTO {
    private String type;
    private Integer min;
    private Integer max;
    private Integer minLength;
    private Integer maxLength;
    private String pattern;
    private String expr;
    private List<String> enumValues;
    private String message;
}
