package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComputeAuditDtoSerdeTest {

    @Test
    void shouldSerializeAndDeserializeComputeAudit() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ComputeAuditDTO audit = ComputeAuditDTO.builder()
                .capabilityType("aggregate")
                .executionBoundary("IN_MEMORY_GROUP_BY")
                .steps(List.of(ComputeStepDTO.builder()
                        .stepName("aggregate")
                        .description("计算聚合")
                        .params(Map.of("field", "amount"))
                        .build()))
                .inputRowCount(2)
                .outputRowCount(1)
                .build();

        String json = objectMapper.writeValueAsString(audit);
        ComputeAuditDTO restored = objectMapper.readValue(json, ComputeAuditDTO.class);

        assertEquals("aggregate", restored.getCapabilityType());
        assertEquals("aggregate", restored.getSteps().get(0).getStepName());
        assertEquals(1, restored.getOutputRowCount());
    }
}
