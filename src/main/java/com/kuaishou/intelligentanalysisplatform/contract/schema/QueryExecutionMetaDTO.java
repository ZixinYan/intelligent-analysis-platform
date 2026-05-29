package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
