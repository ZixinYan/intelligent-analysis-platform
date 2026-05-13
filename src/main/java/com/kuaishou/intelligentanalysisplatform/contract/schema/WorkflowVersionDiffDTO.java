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
public class WorkflowVersionDiffDTO {
    private int fromVersion;
    private int toVersion;
    private List<String> addedNodeIds;
    private List<String> removedNodeIds;
    private List<String> modifiedNodeIds;
    private List<String> addedEdgeIds;
    private List<String> removedEdgeIds;
}
