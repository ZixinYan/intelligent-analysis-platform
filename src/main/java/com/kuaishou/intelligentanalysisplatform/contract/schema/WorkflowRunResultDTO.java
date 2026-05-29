package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowRunResultDTO {
    private String workflowId;
    private ExecutionStatus status;
    private List<NodeResultDTO> nodeResults;
    private StandardResultDTO finalResult;
    private String finalResultNodeId;
}
