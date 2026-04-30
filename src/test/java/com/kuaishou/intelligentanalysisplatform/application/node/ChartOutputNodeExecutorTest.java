package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.application.node.output.DatasetTruncator;
import com.kuaishou.intelligentanalysisplatform.application.node.output.OutputProtocolAssembler;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ChartType;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ResultKind;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ChartMappingDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ChartOutputNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.VariableRefDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChartOutputNodeExecutorTest {

    private final ChartOutputNodeExecutor executor = new ChartOutputNodeExecutor(
            new RuntimeBindingResolver(), new OutputProtocolAssembler(), new DatasetTruncator());

    @Test
    void shouldExecuteChartOutputNode() {
        var result = executor.execute(NodeExecuteContextDTO.builder()
                        .nodeId("chart1")
                        .upstreamResults(Map.of("sql1", StandardResultDTO.builder()
                                .kind(ResultKind.DATASET)
                                .dataset(DatasetDTO.builder().rows(List.of(
                                        Map.of("dt", "2026-04-01", "sales", 10),
                                        Map.of("dt", "2026-04-02", "sales", 20)))
                                        .build())
                                .build()))
                        .build(),
                ChartOutputNodeConfigDTO.builder()
                        .datasetRef(VariableRefDTO.builder().sourceNodeId("sql1").path(List.of("dataset")).build())
                        .chartType(ChartType.LINE)
                        .mapping(ChartMappingDTO.builder().categoryField("dt").seriesFields(List.of("sales")).build())
                        .build());

        assertEquals(ResultKind.CHART, result.getResult().getKind());
        assertEquals(ChartType.LINE, result.getResult().getChart().getChartType());
        assertEquals(List.of("2026-04-01", "2026-04-02"), result.getResult().getChart().getData().getCategories());
    }

    @Test
    void shouldRejectMissingDataset() {
        assertThrows(BaseBusinessException.class, () -> executor.execute(NodeExecuteContextDTO.builder()
                        .nodeId("chart1")
                        .upstreamResults(Map.of())
                        .build(),
                ChartOutputNodeConfigDTO.builder()
                        .datasetRef(VariableRefDTO.builder().sourceNodeId("sql1").path(List.of("dataset")).build())
                        .chartType(ChartType.LINE)
                        .mapping(ChartMappingDTO.builder().categoryField("dt").seriesFields(List.of("sales")).build())
                        .build()));
    }
}
