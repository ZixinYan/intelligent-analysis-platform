package com.kuaishou.intelligentanalysisplatform.contract.schema;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsyncSubmitResponseDTO {
    private String taskId;
    private ExecutionStatus status;
    private String pollUrl;
}
