package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VariableRefDTO {
    private String sourceNodeId;
    private List<String> path;
}
