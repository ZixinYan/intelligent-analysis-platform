package com.kuaishou.intelligentanalysisplatform.infra.observability;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import org.springframework.stereotype.Component;

@Component
public class QueryMetricsService {
    private final AtomicLong totalQueries = new AtomicLong();
    private final AtomicLong succeededQueries = new AtomicLong();
    private final AtomicLong failedQueries = new AtomicLong();
    private final AtomicLong cancelledQueries = new AtomicLong();
    private final AtomicLong timedOutQueries = new AtomicLong();
    private final AtomicLong slowQueries = new AtomicLong();
    private final AtomicLong totalElapsedMs = new AtomicLong();
    private final AtomicLong maxElapsedMs = new AtomicLong();
    private final AtomicInteger currentAsyncQueueDepth = new AtomicInteger();
    private final AtomicInteger currentDatasourceConcurrency = new AtomicInteger();
    private final Map<String, AtomicLong> datasourceSlowQueries = new ConcurrentHashMap<>();

    public void recordQuery(ExecutionStatus status, Long elapsedMs, boolean slowQuery, String datasourceId) {
        totalQueries.incrementAndGet();
        if (status == ExecutionStatus.SUCCEEDED) {
            succeededQueries.incrementAndGet();
        } else if (status == ExecutionStatus.CANCELLED) {
            cancelledQueries.incrementAndGet();
        } else if (status == ExecutionStatus.FAILED) {
            failedQueries.incrementAndGet();
        }
        if (slowQuery) {
            slowQueries.incrementAndGet();
            datasourceSlowQueries.computeIfAbsent(datasourceId == null ? "unknown" : datasourceId, key -> new AtomicLong())
                    .incrementAndGet();
        }
        if (elapsedMs != null && elapsedMs >= 0) {
            totalElapsedMs.addAndGet(elapsedMs);
            maxElapsedMs.accumulateAndGet(elapsedMs, Math::max);
        }
    }

    public void recordTimeout(String datasourceId) {
        timedOutQueries.incrementAndGet();
        failedQueries.incrementAndGet();
        datasourceSlowQueries.computeIfAbsent(datasourceId == null ? "unknown" : datasourceId, key -> new AtomicLong());
    }

    public void updateAsyncQueueDepth(int depth) {
        currentAsyncQueueDepth.set(Math.max(depth, 0));
    }

    public void updateDatasourceConcurrency(int concurrency) {
        currentDatasourceConcurrency.set(Math.max(concurrency, 0));
    }

    public OpsMetricsSnapshot snapshot() {
        long total = totalQueries.get();
        long success = succeededQueries.get();
        return OpsMetricsSnapshot.builder()
                .totalQueries(total)
                .succeededQueries(success)
                .failedQueries(failedQueries.get())
                .cancelledQueries(cancelledQueries.get())
                .timedOutQueries(timedOutQueries.get())
                .slowQueries(slowQueries.get())
                .averageElapsedMs(total == 0 ? 0D : (double) totalElapsedMs.get() / total)
                .maxElapsedMs(maxElapsedMs.get())
                .successRate(total == 0 ? 0D : (double) success / total)
                .asyncQueueDepth(currentAsyncQueueDepth.get())
                .datasourceConcurrency(currentDatasourceConcurrency.get())
                .build();
    }
}
