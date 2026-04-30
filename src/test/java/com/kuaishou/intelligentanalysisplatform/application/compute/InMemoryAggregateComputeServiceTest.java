package com.kuaishou.intelligentanalysisplatform.application.compute;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.enums.AggregateFunction;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AggregateMetricDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AggregateNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InMemoryAggregateComputeServiceTest {
    @Test
    void shouldAggregateRows() {
        InMemoryAggregateComputeService service = new InMemoryAggregateComputeService();
        DatasetDTO result = service.compute(AggregateNodeConfigDTO.builder()
                        .groupByFields(List.of("product"))
                        .metrics(List.of(AggregateMetricDTO.builder().field("amount").agg(AggregateFunction.SUM).alias("total_amount").build()))
                        .build(),
                DatasetDTO.builder().rows(List.of(
                        Map.of("product", "A", "amount", 10),
                        Map.of("product", "A", "amount", 20),
                        Map.of("product", "B", "amount", 5)))
                        .build());

        assertEquals(2, result.getRows().size());
        assertEquals(30, ((java.math.BigDecimal) result.getRows().get(0).get("total_amount")).intValue());
    }
}
