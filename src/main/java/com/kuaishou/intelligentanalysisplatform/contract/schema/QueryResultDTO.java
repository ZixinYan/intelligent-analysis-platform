package com.kuaishou.intelligentanalysisplatform.contract.schema;

import com.kuaishou.intelligentanalysisplatform.common.error.ErrorInfoDTO;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QueryResultDTO {
    private String queryId;
    private ExecutionStatus status;
    private DatasetDTO dataset;
    private QueryExecutionMetaDTO executionMeta;
    private NodeRunMetaDTO computeMeta;
    private ErrorInfoDTO error;
}
