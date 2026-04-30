package com.kuaishou.intelligentanalysisplatform.application.compute;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComputeCapabilityRegistryTest {

    @Test
    void shouldExposeAllComputeCapabilities() {
        ComputeCapabilityRegistry registry = new ComputeCapabilityRegistry(new ObjectMapper());

        assertEquals(6, registry.listAll().size());
        assertNotNull(registry.getByCode("aggregate"));
        assertEquals(Boolean.TRUE, registry.getByCode("formula").getParams().get("derivedMetric"));
        assertTrue(((java.util.List<?>) registry.getByCode("time_series_compute").getCapabilityConfig().getSupportedComputeTypes()).contains("MOM"));
    }
}
