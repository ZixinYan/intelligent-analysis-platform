package com.kuaishou.intelligentanalysisplatform.contract.schema.ai;

import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiWorkflowExecuteResultDTO {
    private boolean supported;
    private String status;
    private String message;
    private String workflowId;
    private String runId;
    private StandardResultDTO finalResult;
    private String finalResultNodeId;
    private String datasetId;
}
