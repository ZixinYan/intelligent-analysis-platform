package com.kuaishou.intelligentanalysisplatform.infra.observability;

import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryGovernanceLimiterTest {
    @Test
    void shouldLimitTenantRateAndDatasourceConcurrency() {
        QueryGovernanceLimiter limiter = new QueryGovernanceLimiter();
        QueryGovernanceLimiter.Lease lease = limiter.acquire("tenant-1", 2, "ds-1", 1);
        assertThat(limiter.currentDatasourceConcurrency()).isEqualTo(1);
        assertThatThrownBy(() -> limiter.acquire("tenant-1", 2, "ds-1", 1))
                .isInstanceOf(BaseBusinessException.class);
        lease.close();
        assertThat(limiter.currentDatasourceConcurrency()).isZero();
    }

    @Test
    void shouldAggregateMetricsSnapshot() {
        QueryMetricsService service = new QueryMetricsService();
        service.recordQuery(ExecutionStatus.SUCCEEDED, 30L, false, "ds-1");
        service.recordQuery(ExecutionStatus.FAILED, 80L, true, "ds-1");
        service.recordTimeout("ds-1");
        service.updateAsyncQueueDepth(3);
        service.updateDatasourceConcurrency(2);
        OpsMetricsSnapshot snapshot = service.snapshot();
        assertThat(snapshot.getTotalQueries()).isEqualTo(2);
        assertThat(snapshot.getFailedQueries()).isEqualTo(2);
        assertThat(snapshot.getSlowQueries()).isEqualTo(1);
        assertThat(snapshot.getTimedOutQueries()).isEqualTo(1);
        assertThat(snapshot.getAsyncQueueDepth()).isEqualTo(3);
        assertThat(snapshot.getDatasourceConcurrency()).isEqualTo(2);
    }
}
