package com.kuaishou.intelligentanalysisplatform.domain.task;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsyncTask {
    private String taskId;
    private TaskType taskType;
    private String refId;
    private String tenantId;
    private String operatorId;
    private ExecutionStatus status;
    private String errorCode;
    private String errorMessage;
    private Long createdAt;
    private Long updatedAt;
}
