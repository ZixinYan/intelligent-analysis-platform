package com.kuaishou.intelligentanalysisplatform.contract.schema.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiClarificationAnswerDTO {
    private String key;
    private String value;
}
