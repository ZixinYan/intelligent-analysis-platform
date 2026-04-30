package com.kuaishou.intelligentanalysisplatform.interfaces.rest.v1;

import com.kuaishou.intelligentanalysisplatform.common.response.ApiResponse;
import com.kuaishou.intelligentanalysisplatform.contract.schema.OpsMetricsSummaryDTO;
import com.kuaishou.intelligentanalysisplatform.infra.observability.OpsMetricsSnapshot;
import com.kuaishou.intelligentanalysisplatform.infra.observability.QueryMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ops")
@RequiredArgsConstructor
public class OpsController {
    private final QueryMetricsService queryMetricsService;

    @GetMapping("/metrics/summary")
    public ApiResponse<OpsMetricsSummaryDTO> summary() {
        OpsMetricsSnapshot snapshot = queryMetricsService.snapshot();
        return ApiResponse.success(OpsMetricsSummaryDTO.builder()
                .totalQueries(snapshot.getTotalQueries())
                .succeededQueries(snapshot.getSucceededQueries())
                .failedQueries(snapshot.getFailedQueries())
                .cancelledQueries(snapshot.getCancelledQueries())
                .timedOutQueries(snapshot.getTimedOutQueries())
                .slowQueries(snapshot.getSlowQueries())
                .averageElapsedMs(snapshot.getAverageElapsedMs())
                .maxElapsedMs(snapshot.getMaxElapsedMs())
                .successRate(snapshot.getSuccessRate())
                .asyncQueueDepth(snapshot.getAsyncQueueDepth())
                .datasourceConcurrency(snapshot.getDatasourceConcurrency())
                .build());
    }
}
