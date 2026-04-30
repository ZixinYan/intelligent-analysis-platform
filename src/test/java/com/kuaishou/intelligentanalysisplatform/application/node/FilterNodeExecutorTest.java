package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.application.NodeMetadataApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeDatasetResolver;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeResultFactory;
import com.kuaishou.intelligentanalysisplatform.application.compute.InMemoryFilterComputeService;
import com.kuaishou.intelligentanalysisplatform.contract.enums.FilterOperator;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ResultKind;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FilterConditionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FilterNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.VariableRefDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class FilterNodeExecutorTest {
    @Test
    void shouldExposeFilterAudit() {
        FilterNodeExecutor executor = new FilterNodeExecutor(mock(NodeMetadataApplicationService.class), new ComputeDatasetResolver(), new InMemoryFilterComputeService(), new ComputeResultFactory());
        var result = executor.execute(NodeExecuteContextDTO.builder()
                        .nodeId("filter1")
                        .requestContext(RequestContextDTO.builder().tenantId("t1").build())
                        .upstreamResults(Map.of("sql1", StandardResultDTO.builder().kind(ResultKind.DATASET).dataset(DatasetDTO.builder().rows(List.of(
                                Map.of("amount", 10),
                                Map.of("amount", 20))).build()).build()))
                        .build(),
                FilterNodeConfigDTO.builder()
                        .datasetRef(VariableRefDTO.builder().sourceNodeId("sql1").path(List.of("dataset")).build())
                        .conditions(List.of(FilterConditionDTO.builder().field("amount").operator(FilterOperator.GT).value(10).build()))
                        .build());
        assertEquals("filter", result.getMeta().getAudit().getCapabilityType());
        assertEquals("filter", result.getMeta().getAudit().getSteps().get(0).getStepName());
        assertEquals(1, result.getResult().getDataset().getRows().size());
    }
}
