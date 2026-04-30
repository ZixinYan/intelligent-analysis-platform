package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpsMetricsSummaryDTO {
    private Long totalQueries;
    private Long succeededQueries;
    private Long failedQueries;
    private Long cancelledQueries;
    private Long timedOutQueries;
    private Long slowQueries;
    private Double averageElapsedMs;
    private Long maxElapsedMs;
    private Double successRate;
    private Integer asyncQueueDepth;
    private Integer datasourceConcurrency;
}
