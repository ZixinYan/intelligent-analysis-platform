package com.kuaishou.intelligentanalysisplatform.infra.repository;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetStatDTO;
import com.kuaishou.intelligentanalysisplatform.domain.dataset.SavedDataset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JdbcTest
@Import({JdbcSavedDatasetRepositoryTest.TestConfig.class, JdbcSavedDatasetRepository.class})
@Sql("classpath:schema.sql")
class JdbcSavedDatasetRepositoryTest {

    @Autowired
    private JdbcSavedDatasetRepository repository;

    @Test
    void shouldSaveAndFindById() {
        repository.save(buildDataset("ds-001", "tenant-a", 1000L));

        SavedDataset found = repository.findById("ds-001").orElseThrow();
        assertEquals("ds-001", found.getDatasetId());
        assertEquals("tenant-a", found.getTenantId());
        assertEquals("Test Dataset", found.getName());
        assertNotNull(found.getSchema());
        assertNotNull(found.getRows());
        assertEquals(2, found.getRows().size());
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
        assertTrue(repository.findById("nonexistent").isEmpty());
    }

    @Test
    void shouldFindByIdAndTenantId() {
        repository.save(buildDataset("ds-002", "tenant-a", 1000L));

        assertTrue(repository.findByIdAndTenantId("ds-002", "tenant-a").isPresent());
        assertTrue(repository.findByIdAndTenantId("ds-002", "tenant-other").isEmpty());
    }

    @Test
    void shouldFindSummaryWithoutRows() {
        repository.save(buildDataset("ds-003", "tenant-a", 1000L));

        List<SavedDataset> summary = repository.findSummaryByTenantId("tenant-a", 10, Long.MAX_VALUE);

        assertFalse(summary.isEmpty());
        SavedDataset ds = summary.stream()
                .filter(d -> "ds-003".equals(d.getDatasetId()))
                .findFirst()
                .orElseThrow();
        // rows_json is not read in summary query
        assertNull(ds.getRows());
        assertNotNull(ds.getSchema());
    }

    @Test
    void shouldPaginateSummaryByUpdatedAt() {
        repository.save(buildDataset("ds-004a", "tenant-b", 1000L));
        repository.save(buildDataset("ds-004b", "tenant-b", 2000L));
        repository.save(buildDataset("ds-004c", "tenant-b", 3000L));

        // Only get datasets updated before 2500
        List<SavedDataset> results = repository.findSummaryByTenantId("tenant-b", 10, 2500L);

        assertEquals(2, results.size());
        // Should be ordered by updated_at DESC
        assertEquals("ds-004b", results.get(0).getDatasetId());
        assertEquals("ds-004a", results.get(1).getDatasetId());
    }

    @Test
    void shouldUpdateMetadata() {
        repository.save(buildDataset("ds-005", "tenant-a", 1000L));

        SavedDataset toUpdate = repository.findByIdAndTenantId("ds-005", "tenant-a").orElseThrow();
        toUpdate.setName("Updated Name");
        toUpdate.setDescription("Updated description");
        toUpdate.setUpdatedAt(9999L);
        repository.update(toUpdate);

        SavedDataset updated = repository.findById("ds-005").orElseThrow();
        assertEquals("Updated Name", updated.getName());
        assertEquals("Updated description", updated.getDescription());
        assertEquals(9999L, updated.getUpdatedAt());
    }

    @Test
    void shouldDeleteByIdAndTenantId() {
        repository.save(buildDataset("ds-006", "tenant-a", 1000L));

        assertTrue(repository.existsByIdAndTenantId("ds-006", "tenant-a"));

        repository.deleteByIdAndTenantId("ds-006", "tenant-a");

        assertFalse(repository.existsByIdAndTenantId("ds-006", "tenant-a"));
        assertTrue(repository.findById("ds-006").isEmpty());
    }

    @Test
    void shouldNotDeleteFromDifferentTenant() {
        repository.save(buildDataset("ds-007", "tenant-a", 1000L));

        repository.deleteByIdAndTenantId("ds-007", "tenant-other");

        assertTrue(repository.existsByIdAndTenantId("ds-007", "tenant-a"));
    }

    @Test
    void shouldExistsByIdAndTenantId() {
        repository.save(buildDataset("ds-008", "tenant-a", 1000L));

        assertTrue(repository.existsByIdAndTenantId("ds-008", "tenant-a"));
        assertFalse(repository.existsByIdAndTenantId("ds-008", "tenant-other"));
        assertFalse(repository.existsByIdAndTenantId("nonexistent", "tenant-a"));
    }

    private SavedDataset buildDataset(String datasetId, String tenantId, long updatedAt) {
        return SavedDataset.builder()
                .datasetId(datasetId)
                .tenantId(tenantId)
                .name("Test Dataset")
                .description("A test dataset")
                .createdBy("user-1")
                .schema(DatasetSchemaDTO.builder().build())
                .stat(DatasetStatDTO.builder().rowCount(2).build())
                .rows(List.of(
                        Map.of("id", 1, "name", "Alice"),
                        Map.of("id", 2, "name", "Bob")))
                .sourceWorkflowId("wf-1")
                .sourceNodeId("node-1")
                .createdAt(1000L)
                .updatedAt(updatedAt)
                .build();
    }

    @Configuration
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
