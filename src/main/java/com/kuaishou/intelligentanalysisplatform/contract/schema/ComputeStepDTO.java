package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComputeStepDTO {
    private String stepName;
    private String description;
    private Map<String, Object> params;
}
