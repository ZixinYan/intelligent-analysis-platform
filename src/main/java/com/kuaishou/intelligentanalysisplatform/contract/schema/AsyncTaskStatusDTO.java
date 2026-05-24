package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.common.error.ErrorInfoDTO;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiClarificationQuestionDTO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AsyncTaskStatusDTO {
    private String taskId;
    private String agentTaskId;
    private String taskType;
    private ExecutionStatus status;
    private Integer progress;
    private DatasetDTO dataset;
    private WorkflowRunResultDTO result;
    private AiClarificationQuestionDTO clarification;
    private Map<String, Object> trace;
    private Double confidence;
    private ErrorInfoDTO error;
    private Long createdAt;
    private Long updatedAt;
}
