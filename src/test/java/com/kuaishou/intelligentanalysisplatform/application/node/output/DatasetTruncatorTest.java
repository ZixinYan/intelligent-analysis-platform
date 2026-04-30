package com.kuaishou.intelligentanalysisplatform.application.node.output;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasetTruncatorTest {

    private final DatasetTruncator truncator = new DatasetTruncator();

    @Test
    void shouldTruncateChartDataset() {
        DatasetDTO dataset = DatasetDTO.builder()
                .rows(List.of(
                        Map.of("id", 1),
                        Map.of("id", 2),
                        Map.of("id", 3)))
                .build();

        var result = truncator.truncate(dataset, 2);

        assertTrue(result.partial());
        assertEquals(3, result.totalRows());
        assertEquals(2, result.returnedRows());
        assertEquals(2, result.dataset().getRows().size());
        assertTrue(result.dataset().getStat().getTruncated());
    }

    @Test
    void shouldKeepDatasetWhenWithinLimit() {
        DatasetDTO dataset = DatasetDTO.builder()
                .rows(List.of(Map.of("id", 1)))
                .build();

        var result = truncator.truncate(dataset, 2);

        assertFalse(result.partial());
        assertEquals(1, result.totalRows());
        assertEquals(1, result.returnedRows());
        assertFalse(result.dataset().getStat().getTruncated());
    }
}
