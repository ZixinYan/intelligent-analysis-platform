package com.kuaishou.intelligentanalysisplatform.contract.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowEdgeDTO {
    private String id;
    private String source;
    private String target;
    private String sourceHandle;
    private String targetHandle;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String condition;  // "true" | "false" | null（null = 无条件边）
}
