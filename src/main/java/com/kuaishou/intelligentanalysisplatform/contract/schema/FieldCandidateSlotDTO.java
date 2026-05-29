package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldCandidateSlotDTO {
    private String slot;
    private Boolean required;
    private List<String> acceptedTypes;
    private List<String> acceptedCapabilities;
    private List<FieldMappingCandidateDTO> candidates;
    private Map<String, Object> extensions;
}
