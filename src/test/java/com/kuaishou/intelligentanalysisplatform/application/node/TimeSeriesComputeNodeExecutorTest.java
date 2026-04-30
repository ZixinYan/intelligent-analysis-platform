package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.application.NodeMetadataApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeDatasetResolver;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeResultFactory;
import com.kuaishou.intelligentanalysisplatform.application.compute.InMemoryTimeSeriesComputeService;
import com.kuaishou.intelligentanalysisplatform.contract.enums.CompareUnit;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ResultKind;
import com.kuaishou.intelligentanalysisplatform.contract.enums.TimeGranularity;
import com.kuaishou.intelligentanalysisplatform.contract.enums.TimeSeriesComputeType;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.MetricComputeRuleDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.TimeSeriesComputeNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.VariableRefDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TimeSeriesComputeNodeExecutorTest {
    @Test
    void shouldExecuteTimeSeriesNode() {
        TimeSeriesComputeNodeExecutor executor = new TimeSeriesComputeNodeExecutor(mock(NodeMetadataApplicationService.class), new ComputeDatasetResolver(), new InMemoryTimeSeriesComputeService(), new ComputeResultFactory());
        var result = executor.execute(NodeExecuteContextDTO.builder()
                        .nodeId("ts1")
                        .requestContext(RequestContextDTO.builder().tenantId("t1").build())
                        .upstreamResults(Map.of("sql1", StandardResultDTO.builder().kind(ResultKind.DATASET).dataset(DatasetDTO.builder().rows(List.of(
                                Map.of("dt", "2026-01-01", "amount", 100),
                                Map.of("dt", "2026-02-01", "amount", 120))).build()).build()))
                        .build(),
                TimeSeriesComputeNodeConfigDTO.builder()
                        .datasetRef(VariableRefDTO.builder().sourceNodeId("sql1").path(List.of("dataset")).build())
                        .timeField("dt")
                        .granularity(TimeGranularity.MONTH)
                        .metrics(List.of(MetricComputeRuleDTO.builder().metricField("amount").computeType(TimeSeriesComputeType.MOM).compareShift(1).compareUnit(CompareUnit.PERIOD).alias("amount_mom").build()))
                        .build());
        assertEquals(ResultKind.DATASET, result.getResult().getKind());
        assertEquals(2, result.getResult().getDataset().getRows().size());
        assertEquals("IN_MEMORY_PERIOD_SHIFT", result.getMeta().getExecutionBoundary());
        assertEquals("period_align", result.getMeta().getAudit().getSteps().get(0).getStepName());
        assertTrue(result.getMeta().getAudit().getSteps().stream().anyMatch(step -> "mom".equals(step.getStepName())));
    }
}
