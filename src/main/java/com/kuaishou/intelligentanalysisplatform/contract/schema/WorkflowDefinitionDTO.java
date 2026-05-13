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
public class WorkflowDefinitionDTO {
    private String workflowId;
    private String workflowName;
    private List<WorkflowNodeDTO> nodes;
    private List<WorkflowEdgeDTO> edges;
    private Map<String, WorkflowPositionDTO> positions;
    private Long createdAt;
    private Long updatedAt;
    private String currentVersionId;
    private Integer currentVersionNumber;
    private String publishedVersionId;
    private Integer publishedVersionNumber;
}
