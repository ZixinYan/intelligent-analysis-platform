package com.kuaishou.intelligentanalysisplatform.application.node.output;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ChartType;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ValueType;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ChartMappingDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ChartOptionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.TableColumnMappingDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.TableOptionDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputProtocolAssemblerTest {

    private final OutputProtocolAssembler assembler = new OutputProtocolAssembler();

    @Test
    void shouldAssembleLineChart() {
        DatasetDTO dataset = DatasetDTO.builder()
                .rows(List.of(
                        Map.of("dt", "2026-04-01", "sales", 10),
                        Map.of("dt", "2026-04-02", "sales", 20)))
                .build();

        var output = assembler.assembleChart(dataset,
                ChartMappingDTO.builder().categoryField("dt").seriesFields(List.of("sales")).build(),
                ChartType.LINE,
                ChartOptionDTO.builder().legend(true).build(),
                "node1");

        assertEquals(ChartType.LINE, output.getChartType());
        assertEquals(List.of("2026-04-01", "2026-04-02"), output.getData().getCategories());
        assertEquals(1, output.getData().getSeries().size());
        assertEquals(List.of(10, 20), output.getData().getSeries().get(0).getData());
        assertFalse(output.getMeta().getPartial());
        assertEquals(2, output.getMeta().getTotalRows());
    }

    @Test
    void shouldAssembleTableWithSchemaFallback() {
        DatasetDTO dataset = DatasetDTO.builder()
                .schema(DatasetSchemaDTO.builder().fields(List.of(
                        FieldSchemaDTO.builder().fieldId("name").name("name").displayName("名称").valueType(ValueType.STRING).build(),
                        FieldSchemaDTO.builder().fieldId("amount").name("amount").displayName("金额").valueType(ValueType.INTEGER).build()))
                        .build())
                .rows(List.of(Map.of("name", "A", "amount", 100)))
                .build();

        var output = assembler.assembleTable(dataset, null, TableOptionDTO.builder().downloadable(true).build(), "node2");

        assertEquals(2, output.getColumns().size());
        assertEquals("name", output.getColumns().get(0).getField());
        assertEquals("名称", output.getColumns().get(0).getLabel());
        assertTrue(output.getMeta().getDownloadable());
    }

    @Test
    void shouldAssembleTableWithColumnMappings() {
        DatasetDTO dataset = DatasetDTO.builder()
                .rows(List.of(Map.of("sales", 100)))
                .build();

        var output = assembler.assembleTable(dataset,
                List.of(TableColumnMappingDTO.builder().sourceField("sales").targetField("gmv").label("GMV").build()),
                null,
                "node3");

        assertEquals(1, output.getColumns().size());
        assertEquals("gmv", output.getColumns().get(0).getField());
        assertEquals("GMV", output.getColumns().get(0).getLabel());
    }
}
