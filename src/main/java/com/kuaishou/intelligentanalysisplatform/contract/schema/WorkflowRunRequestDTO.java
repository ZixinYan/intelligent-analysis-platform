package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowRunRequestDTO {
    private String workflowId;
    private Map<String, Object> inputs;
    private List<WorkflowNodeDTO> nodes;
    private List<WorkflowEdgeDTO> edges;
    private Boolean async;
    private RequestContextDTO context;
}
