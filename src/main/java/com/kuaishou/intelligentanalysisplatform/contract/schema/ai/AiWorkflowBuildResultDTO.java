package com.kuaishou.intelligentanalysisplatform.contract.schema.ai;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowDefinitionDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiWorkflowBuildResultDTO {
    private String responseType;
    private String buildMode;
    private WorkflowDefinitionDTO draft;
    private String agentTaskId;
    private List<AiClarificationQuestionDTO> clarifications;
    private Boolean saved;
    private String workflowId;
    private String datasetId;
    private AiWorkflowExecuteResultDTO execution;
    private Map<String, Object> metadata;
}
