package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.application.NodeMetadataApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeDatasetResolver;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeResultFactory;
import com.kuaishou.intelligentanalysisplatform.application.compute.InMemoryAggregateComputeService;
import com.kuaishou.intelligentanalysisplatform.contract.enums.AggregateFunction;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ResultKind;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AggregateMetricDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AggregateNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SortFieldDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.VariableRefDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AggregateNodeExecutorTest {
    @Test
    void shouldExecuteAggregateNode() {
        NodeMetadataApplicationService metadataService = mock(NodeMetadataApplicationService.class);
        AggregateNodeExecutor executor = new AggregateNodeExecutor(metadataService, new ComputeDatasetResolver(), new InMemoryAggregateComputeService(), new ComputeResultFactory());
        var result = executor.execute(NodeExecuteContextDTO.builder()
                        .nodeId("agg1")
                        .requestContext(RequestContextDTO.builder().tenantId("t1").build())
                        .upstreamResults(Map.of("sql1", StandardResultDTO.builder().kind(ResultKind.DATASET).dataset(DatasetDTO.builder().rows(List.of(
                                Map.of("product", "A", "amount", 10),
                                Map.of("product", "A", "amount", 20))).build()).build()))
                        .build(),
                AggregateNodeConfigDTO.builder()
                        .datasetRef(VariableRefDTO.builder().sourceNodeId("sql1").path(List.of("dataset")).build())
                        .groupByFields(List.of("product"))
                        .metrics(List.of(AggregateMetricDTO.builder().field("amount").agg(AggregateFunction.SUM).alias("total").build()))
                        .sortFields(List.of(SortFieldDTO.builder().field("total").order("desc").build()))
                        .topN(10)
                        .build());
        assertEquals(ResultKind.DATASET, result.getResult().getKind());
        assertEquals(30, ((java.math.BigDecimal) result.getResult().getDataset().getRows().get(0).get("total")).intValue());
        assertEquals("aggregate", result.getMeta().getAudit().getCapabilityType());
        assertEquals("group_by", result.getMeta().getAudit().getSteps().get(0).getStepName());
        assertEquals("IN_MEMORY_GROUP_BY", result.getMeta().getExecutionBoundary());
    }
}
