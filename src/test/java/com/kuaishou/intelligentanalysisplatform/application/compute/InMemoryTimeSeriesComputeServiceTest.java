package com.kuaishou.intelligentanalysisplatform.application.compute;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.enums.CompareUnit;
import com.kuaishou.intelligentanalysisplatform.contract.enums.TimeGranularity;
import com.kuaishou.intelligentanalysisplatform.contract.enums.TimeSeriesComputeType;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.MetricComputeRuleDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.TimeSeriesComputeNodeConfigDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InMemoryTimeSeriesComputeServiceTest {
    @Test
    void shouldComputeMomRatioOnAggregatedPeriod() {
        InMemoryTimeSeriesComputeService service = new InMemoryTimeSeriesComputeService();
        DatasetDTO result = service.compute(TimeSeriesComputeNodeConfigDTO.builder()
                        .timeField("dt")
                        .granularity(TimeGranularity.MONTH)
                        .metrics(List.of(MetricComputeRuleDTO.builder()
                                .metricField("amount")
                                .computeType(TimeSeriesComputeType.MOM)
                                .compareShift(1)
                                .compareUnit(CompareUnit.PERIOD)
                                .alias("amount_mom")
                                .build()))
                        .build(),
                DatasetDTO.builder().rows(List.of(
                        Map.of("dt", "2026-01-01", "amount", 40),
                        Map.of("dt", "2026-01-12", "amount", 60),
                        Map.of("dt", "2026-02-01", "amount", 120)))
                        .build());

        assertEquals(new BigDecimal("20.00"), result.getRows().get(1).get("amount_mom"));
    }

    @Test
    void shouldComputeMovingAverage() {
        InMemoryTimeSeriesComputeService service = new InMemoryTimeSeriesComputeService();
        DatasetDTO result = service.compute(TimeSeriesComputeNodeConfigDTO.builder()
                        .timeField("dt")
                        .granularity(TimeGranularity.MONTH)
                        .metrics(List.of(MetricComputeRuleDTO.builder()
                                .metricField("amount")
                                .computeType(TimeSeriesComputeType.MOVING_AVG)
                                .windowSize(2)
                                .alias("amount_ma")
                                .build()))
                        .build(),
                DatasetDTO.builder().rows(List.of(
                        Map.of("dt", "2026-01-01", "amount", 100),
                        Map.of("dt", "2026-02-01", "amount", 120),
                        Map.of("dt", "2026-03-01", "amount", 140)))
                        .build());

        assertEquals(new BigDecimal("130.0000"), result.getRows().get(2).get("amount_ma"));
    }

    @Test
    void shouldReturnNullForFirstMomPeriod() {
        InMemoryTimeSeriesComputeService service = new InMemoryTimeSeriesComputeService();
        DatasetDTO result = service.compute(TimeSeriesComputeNodeConfigDTO.builder()
                        .timeField("dt")
                        .granularity(TimeGranularity.MONTH)
                        .metrics(List.of(MetricComputeRuleDTO.builder()
                                .metricField("amount")
                                .computeType(TimeSeriesComputeType.MOM)
                                .alias("amount_mom")
                                .build()))
                        .build(),
                DatasetDTO.builder().rows(List.of(Map.of("dt", "2026-01-01", "amount", 100))).build());

        assertNull(result.getRows().get(0).get("amount_mom"));
    }
}
