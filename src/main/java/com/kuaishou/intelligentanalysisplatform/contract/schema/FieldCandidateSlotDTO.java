package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FieldCandidateSlotDTO {
    private String slot;
    private Boolean required;
    private List<String> acceptedTypes;
    private List<String> acceptedCapabilities;
    private List<FieldMappingCandidateDTO> candidates;
    private Map<String, Object> extensions;
}
