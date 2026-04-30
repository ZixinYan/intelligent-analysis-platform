package com.kuaishou.intelligentanalysisplatform.application.compute;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FormulaFieldDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FormulaNodeConfigDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InMemoryFormulaComputeServiceTest {
    @Test
    void shouldCalculateFormula() {
        InMemoryFormulaComputeService service = new InMemoryFormulaComputeService();
        DatasetDTO result = service.compute(FormulaNodeConfigDTO.builder()
                        .formulas(List.of(FormulaFieldDTO.builder().alias("profit").expression("income-cost").build()))
                        .build(),
                DatasetDTO.builder().rows(List.of(Map.of("income", 100, "cost", 30))).build());
        assertEquals(70, ((java.math.BigDecimal) result.getRows().get(0).get("profit")).intValue());
    }

    @Test
    void shouldReturnNullWhenDivideByZero() {
        InMemoryFormulaComputeService service = new InMemoryFormulaComputeService();
        DatasetDTO result = service.compute(FormulaNodeConfigDTO.builder()
                        .formulas(List.of(FormulaFieldDTO.builder().alias("ratio").expression("income/cost").build()))
                        .build(),
                DatasetDTO.builder().rows(List.of(Map.of("income", 100, "cost", 0))).build());
        assertNull(result.getRows().get(0).get("ratio"));
    }
}
