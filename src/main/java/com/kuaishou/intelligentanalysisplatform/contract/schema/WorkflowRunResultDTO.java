package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkflowRunResultDTO {
    private String workflowId;
    private ExecutionStatus status;
    private List<NodeResultDTO> nodeResults;
    private StandardResultDTO finalResult;
}
