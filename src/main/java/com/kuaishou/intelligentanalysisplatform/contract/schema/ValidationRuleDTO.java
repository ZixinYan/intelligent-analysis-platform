package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
