package com.kuaishou.intelligentanalysisplatform.application;

import com.kuaishou.intelligentanalysisplatform.common.response.PageResult;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowDefinitionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowQueryRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowSaveRequestDTO;

public interface WorkflowApplicationService {
    WorkflowDefinitionDTO create(WorkflowSaveRequestDTO request);

    WorkflowDefinitionDTO update(String workflowId, WorkflowSaveRequestDTO request);

    WorkflowDefinitionDTO getById(String workflowId, RequestContextDTO context);

    PageResult<WorkflowDefinitionDTO> list(WorkflowQueryRequestDTO request);
}
