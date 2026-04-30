package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QueryExecutionMetaDTO {
    private String queryId;
    private String mode;
    private Long startedAt;
    private Long finishedAt;
    private Long elapsedMs;
    private Boolean cached;
    private Boolean truncated;
    private Integer rowCount;
    private Integer returnedRowCount;
    private String engineType;
}
