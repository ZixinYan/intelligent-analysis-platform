package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.application.node.output.DatasetTruncator;
import com.kuaishou.intelligentanalysisplatform.application.node.output.OutputProtocolAssembler;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ResultKind;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.TableColumnMappingDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.TableOptionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.TableOutputNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.VariableRefDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableOutputNodeExecutorTest {

    private final TableOutputNodeExecutor executor = new TableOutputNodeExecutor(
            new RuntimeBindingResolver(), new OutputProtocolAssembler(), new DatasetTruncator());

    @Test
    void shouldExecuteTableOutputNode() {
        var result = executor.execute(NodeExecuteContextDTO.builder()
                        .nodeId("table1")
                        .upstreamResults(Map.of("sql1", StandardResultDTO.builder()
                                .kind(ResultKind.DATASET)
                                .dataset(DatasetDTO.builder().rows(List.of(Map.of("sales", 100))).build())
                                .build()))
                        .build(),
                TableOutputNodeConfigDTO.builder()
                        .datasetRef(VariableRefDTO.builder().sourceNodeId("sql1").path(List.of("dataset")).build())
                        .columns(List.of(TableColumnMappingDTO.builder().sourceField("sales").targetField("gmv").label("GMV").build()))
                        .option(TableOptionDTO.builder().downloadable(true).build())
                        .build());

        assertEquals(ResultKind.TABLE, result.getResult().getKind());
        assertEquals("GMV", result.getResult().getTable().getColumns().get(0).getLabel());
        assertTrue(result.getResult().getTable().getMeta().getDownloadable());
    }

    @Test
    void shouldRejectMissingDataset() {
        assertThrows(BaseBusinessException.class, () -> executor.execute(NodeExecuteContextDTO.builder()
                        .nodeId("table1")
                        .upstreamResults(Map.of())
                        .build(),
                TableOutputNodeConfigDTO.builder()
                        .datasetRef(VariableRefDTO.builder().sourceNodeId("sql1").path(List.of("dataset")).build())
                        .build()));
    }
}
