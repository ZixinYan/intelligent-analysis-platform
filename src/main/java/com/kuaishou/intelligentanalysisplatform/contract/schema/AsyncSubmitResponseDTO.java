package com.kuaishou.intelligentanalysisplatform.contract.schema;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AsyncSubmitResponseDTO {
    private String taskId;
    private ExecutionStatus status;
    private String pollUrl;
}
