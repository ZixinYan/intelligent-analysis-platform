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
public class MappingCandidateRequestDTO {
    private String renderer;
    private List<FieldSchemaDTO> upstreamFields;
}
