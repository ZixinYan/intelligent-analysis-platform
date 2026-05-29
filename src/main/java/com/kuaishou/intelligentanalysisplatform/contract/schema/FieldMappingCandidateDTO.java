package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldMappingCandidateDTO {
    @JsonProperty("field")
    private String fieldId;

    private Double score;

    @JsonProperty("reason")
    private String reason;

    private Map<String, Object> extensions;
}
