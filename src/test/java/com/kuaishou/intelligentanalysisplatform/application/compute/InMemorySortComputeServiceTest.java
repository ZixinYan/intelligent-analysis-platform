package com.kuaishou.intelligentanalysisplatform.application.compute;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SortFieldDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SortNodeConfigDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InMemorySortComputeServiceTest {
    @Test
    void shouldSortRows() {
        InMemorySortComputeService service = new InMemorySortComputeService();
        DatasetDTO result = service.compute(SortNodeConfigDTO.builder()
                        .sortFields(List.of(SortFieldDTO.builder().field("amount").order("DESC").build()))
                        .limit(1)
                        .build(),
                DatasetDTO.builder().rows(List.of(Map.of("amount", 5), Map.of("amount", 15))).build());
        assertEquals(15, result.getRows().get(0).get("amount"));
    }
}
