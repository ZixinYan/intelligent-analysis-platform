package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FieldMappingCandidateDTO {
    private String fieldId;
    private Double score;
    private List<String> reasons;
    private Map<String, Object> extensions;
}
