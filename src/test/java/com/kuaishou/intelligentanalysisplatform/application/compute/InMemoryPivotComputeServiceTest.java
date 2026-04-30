package com.kuaishou.intelligentanalysisplatform.application.compute;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.PivotNodeConfigDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InMemoryPivotComputeServiceTest {
    @Test
    void shouldPivotRows() {
        InMemoryPivotComputeService service = new InMemoryPivotComputeService();
        DatasetDTO result = service.compute(PivotNodeConfigDTO.builder()
                        .rowFields(List.of("product"))
                        .columnField("month")
                        .valueField("amount")
                        .build(),
                DatasetDTO.builder().rows(List.of(
                        Map.of("product", "A", "month", "2026-01", "amount", 10),
                        Map.of("product", "A", "month", "2026-02", "amount", 20)))
                        .build());
        assertEquals(1, result.getRows().size());
        assertEquals(20, result.getRows().get(0).get("2026-02"));
    }
}
