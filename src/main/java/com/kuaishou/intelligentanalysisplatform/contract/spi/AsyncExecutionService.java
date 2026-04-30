package com.kuaishou.intelligentanalysisplatform.contract.spi;

import com.kuaishou.intelligentanalysisplatform.contract.schema.AsyncSubmitResponseDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AsyncTaskStatusDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeDebugRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowRunRequestDTO;

public interface AsyncExecutionService {
    AsyncSubmitResponseDTO submitWorkflow(WorkflowRunRequestDTO request);
    AsyncSubmitResponseDTO submitNode(NodeDebugRequestDTO request);
    AsyncTaskStatusDTO getTask(String taskId);
    void cancelTask(String taskId);
}
