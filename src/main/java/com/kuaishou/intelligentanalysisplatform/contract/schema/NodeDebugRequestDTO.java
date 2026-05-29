package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeDebugRequestDTO {
    private String workflowId;
    private String nodeId;
    private WorkflowNodeDTO node;
    private List<WorkflowNodeDTO> allNodes;
    private Map<String, Object> upstreamMockInputs;
    private Boolean async;
    private DebugOptionDTO option;
    private RequestContextDTO context;
}
