package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FieldMappingCandidateDTO {
    @JsonProperty("field")
    private String fieldId;

    private Double score;

    @JsonProperty("reason")
    private String reason;

    private Map<String, Object> extensions;
}
