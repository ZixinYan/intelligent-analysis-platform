package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.application.NodeMetadataApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeDatasetResolver;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeResultFactory;
import com.kuaishou.intelligentanalysisplatform.application.compute.InMemoryFormulaComputeService;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ResultKind;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FormulaFieldDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FormulaNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.VariableRefDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class FormulaNodeExecutorTest {
    @Test
    void shouldExposeDerivedMetricAudit() {
        FormulaNodeExecutor executor = new FormulaNodeExecutor(mock(NodeMetadataApplicationService.class), new ComputeDatasetResolver(), new InMemoryFormulaComputeService(), new ComputeResultFactory());
        var result = executor.execute(NodeExecuteContextDTO.builder()
                        .nodeId("formula1")
                        .requestContext(RequestContextDTO.builder().tenantId("t1").build())
                        .upstreamResults(Map.of("sql1", StandardResultDTO.builder().kind(ResultKind.DATASET).dataset(DatasetDTO.builder().rows(List.of(
                                Map.of("amount", 10, "tax", 2),
                                Map.of("amount", 20, "tax", 4))).build()).build()))
                        .build(),
                FormulaNodeConfigDTO.builder()
                        .datasetRef(VariableRefDTO.builder().sourceNodeId("sql1").path(List.of("dataset")).build())
                        .formulas(List.of(FormulaFieldDTO.builder().alias("gross").expression("amount + tax").build()))
                        .build());
        assertEquals(ResultKind.DATASET, result.getResult().getKind());
        assertEquals("formula", result.getMeta().getAudit().getCapabilityType());
        assertTrue(result.getMeta().getAudit().getDerivedMetricNote().contains("DERIVED_METRIC"));
        assertEquals("formula_eval", result.getMeta().getAudit().getSteps().get(0).getStepName());
    }
}
