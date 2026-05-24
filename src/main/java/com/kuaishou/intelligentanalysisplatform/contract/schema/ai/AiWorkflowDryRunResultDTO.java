package com.kuaishou.intelligentanalysisplatform.contract.schema.ai;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiWorkflowDryRunResultDTO {
    private boolean supported;
    private String status;
    private String message;
    private String workflowId;
    private List<String> warnings;
}
