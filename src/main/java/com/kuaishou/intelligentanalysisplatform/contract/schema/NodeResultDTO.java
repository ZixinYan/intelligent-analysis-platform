package com.kuaishou.intelligentanalysisplatform.contract.schema;

import com.kuaishou.intelligentanalysisplatform.common.error.ErrorInfoDTO;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeResultDTO {
    private String nodeId;
    private String nodeType;
    private ExecutionStatus status;
    private StandardResultDTO result;
    private ErrorInfoDTO error;
    private NodeRunMetaDTO meta;
}
