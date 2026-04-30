package com.kuaishou.intelligentanalysisplatform.infra.observability;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OpsMetricsSnapshot {
    long totalQueries;
    long succeededQueries;
    long failedQueries;
    long cancelledQueries;
    long timedOutQueries;
    long slowQueries;
    double averageElapsedMs;
    long maxElapsedMs;
    double successRate;
    int asyncQueueDepth;
    int datasourceConcurrency;
}
