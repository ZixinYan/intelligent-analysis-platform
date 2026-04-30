package com.kuaishou.intelligentanalysisplatform.application.compute;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.enums.FilterOperator;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FilterConditionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FilterNodeConfigDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InMemoryFilterComputeServiceTest {
    @Test
    void shouldFilterRows() {
        InMemoryFilterComputeService service = new InMemoryFilterComputeService();
        DatasetDTO result = service.compute(FilterNodeConfigDTO.builder()
                        .conditions(List.of(FilterConditionDTO.builder().field("amount").operator(FilterOperator.GT).value(10).build()))
                        .build(),
                DatasetDTO.builder().rows(List.of(Map.of("amount", 5), Map.of("amount", 15))).build());
        assertEquals(1, result.getRows().size());
    }
}
