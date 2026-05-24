package com.kuaishou.intelligentanalysisplatform.contract.schema.ai;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowEdgeDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowNodeDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowPositionDTO;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiWorkflowSaveRequestDTO {
    private String workflowId;
    @NotBlank
    private String workflowName;
    private List<WorkflowNodeDTO> nodes;
    private List<WorkflowEdgeDTO> edges;
    private Map<String, WorkflowPositionDTO> positions;
}
