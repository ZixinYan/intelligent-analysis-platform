package com.kuaishou.intelligentanalysisplatform.contract.spi;

import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeDebugRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowRunRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowRunResultDTO;

public interface SyncExecutionService {
    NodeResultDTO runNode(NodeDebugRequestDTO request);
    WorkflowRunResultDTO runWorkflow(WorkflowRunRequestDTO request);
}
