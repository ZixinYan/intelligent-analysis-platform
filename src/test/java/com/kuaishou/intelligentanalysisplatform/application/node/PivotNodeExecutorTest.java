package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.application.NodeMetadataApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeDatasetResolver;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeResultFactory;
import com.kuaishou.intelligentanalysisplatform.application.compute.InMemoryPivotComputeService;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ResultKind;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.PivotNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.VariableRefDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class PivotNodeExecutorTest {
    @Test
    void shouldExposePivotAudit() {
        PivotNodeExecutor executor = new PivotNodeExecutor(mock(NodeMetadataApplicationService.class), new ComputeDatasetResolver(), new InMemoryPivotComputeService(), new ComputeResultFactory());
        var result = executor.execute(NodeExecuteContextDTO.builder()
                        .nodeId("pivot1")
                        .requestContext(RequestContextDTO.builder().tenantId("t1").build())
                        .upstreamResults(Map.of("sql1", StandardResultDTO.builder().kind(ResultKind.DATASET).dataset(DatasetDTO.builder().rows(List.of(
                                Map.of("region", "A", "month", "2026-01", "amount", 10),
                                Map.of("region", "A", "month", "2026-02", "amount", 12))).build()).build()))
                        .build(),
                PivotNodeConfigDTO.builder()
                        .datasetRef(VariableRefDTO.builder().sourceNodeId("sql1").path(List.of("dataset")).build())
                        .rowFields(List.of("region"))
                        .columnField("month")
                        .valueField("amount")
                        .build());
        assertEquals(ResultKind.DATASET, result.getResult().getKind());
        assertEquals("pivot", result.getMeta().getAudit().getCapabilityType());
        assertEquals("pivot", result.getMeta().getAudit().getSteps().get(0).getStepName());
    }
}
