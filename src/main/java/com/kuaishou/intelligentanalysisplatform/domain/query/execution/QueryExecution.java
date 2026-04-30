package com.kuaishou.intelligentanalysisplatform.domain.query.execution;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryExecution {
    private String queryId;
    private String tenantId;
    private String datasourceId;
    private String sqlFingerprint;
    private String mode;
    private ExecutionStatus status;
    private Long startedAt;
    private Long finishedAt;
    private Long elapsedMs;
    private Boolean cached;
    private Boolean truncated;
    private Integer rowCount;
    private String errorCode;
    private String errorMessage;
    private String operatorId;
    private Long createdAt;
}
