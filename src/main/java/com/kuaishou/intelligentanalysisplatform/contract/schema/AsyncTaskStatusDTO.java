package com.kuaishou.intelligentanalysisplatform.contract.schema;

import com.kuaishou.intelligentanalysisplatform.common.error.ErrorInfoDTO;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AsyncTaskStatusDTO {
    private String taskId;
    private String taskType;
    private ExecutionStatus status;
    private Integer progress;
    private DatasetDTO dataset;
    private WorkflowRunResultDTO result;
    private ErrorInfoDTO error;
    private Long createdAt;
    private Long updatedAt;
}
