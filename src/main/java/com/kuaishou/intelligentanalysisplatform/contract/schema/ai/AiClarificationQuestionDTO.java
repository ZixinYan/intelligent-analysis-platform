package com.kuaishou.intelligentanalysisplatform.contract.schema.ai;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiClarificationQuestionDTO {
    private String key;
    private String label;
    private Boolean required;
    private String hint;
    private String inputType;
    private List<Map<String, String>> options;
}
