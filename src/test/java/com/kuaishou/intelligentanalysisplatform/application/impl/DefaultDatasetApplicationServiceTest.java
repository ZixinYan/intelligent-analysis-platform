package com.kuaishou.intelligentanalysisplatform.application.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetStatDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SaveDatasetRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SavedDatasetSummaryDTO;
import com.kuaishou.intelligentanalysisplatform.domain.dataset.SavedDataset;
import com.kuaishou.intelligentanalysisplatform.domain.dataset.SavedDatasetRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultDatasetApplicationServiceTest {

    private final SavedDatasetRepository repository = mock(SavedDatasetRepository.class);
    private final DefaultDatasetApplicationService service = new DefaultDatasetApplicationService(repository);

    @Test
    void shouldSaveDatasetSuccessfully() {
        SaveDatasetRequestDTO request = buildRequest("test-dataset", 10);

        SavedDatasetSummaryDTO result = service.save(request, "tenant-a", "user-1");

        verify(repository).save(any(SavedDataset.class));
        assertEquals("test-dataset", result.getName());
        assertEquals("tenant-a", result.getTenantId());
        assertEquals("user-1", result.getCreatedBy());
    }

    @Test
    void shouldRejectDatasetExceedingMaxRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 100_001; i++) {
            rows.add(Map.of("id", i));
        }
        DatasetDTO dataset = DatasetDTO.builder()
                .schema(DatasetSchemaDTO.builder().build())
                .rows(rows)
                .build();
        SaveDatasetRequestDTO request = SaveDatasetRequestDTO.builder()
                .name("too-large")
                .dataset(dataset)
                .build();

        BaseBusinessException ex = assertThrows(BaseBusinessException.class,
                () -> service.save(request, "tenant-a", "user-1"));

        assertEquals(ErrorCode.DATASET_TOO_LARGE, ex.getErrorCode());
    }

    @Test
    void shouldRejectSaveWithoutName() {
        SaveDatasetRequestDTO request = SaveDatasetRequestDTO.builder()
                .dataset(DatasetDTO.builder().build())
                .build();

        BaseBusinessException ex = assertThrows(BaseBusinessException.class,
                () -> service.save(request, "tenant-a", "user-1"));

        assertEquals(ErrorCode.INVALID_ARGUMENT, ex.getErrorCode());
    }

    @Test
    void shouldListDatasets() {
        when(repository.findSummaryByTenantId("tenant-a", 10, Long.MAX_VALUE))
                .thenReturn(List.of(buildSavedDataset("ds-1")));

        List<SavedDatasetSummaryDTO> result = service.list("tenant-a", 10, null);

        assertEquals(1, result.size());
        assertEquals("ds-1", result.get(0).getDatasetId());
    }

    @Test
    void shouldGetDatasetPage() {
        List<Map<String, Object>> allRows = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            allRows.add(Map.of("idx", i));
        }
        SavedDataset dataset = buildSavedDatasetWithRows("ds-paged", allRows);
        when(repository.findByIdAndTenantId("ds-paged", "tenant-a")).thenReturn(Optional.of(dataset));

        DatasetDTO page0 = service.getDatasetPage("ds-paged", "tenant-a", 0, 10);
        DatasetDTO page2 = service.getDatasetPage("ds-paged", "tenant-a", 2, 10);

        assertEquals(10, page0.getRows().size());
        assertEquals(5, page2.getRows().size());
    }

    @Test
    void shouldReturnEmptyPageWhenBeyondRange() {
        SavedDataset dataset = buildSavedDatasetWithRows("ds-empty-page", List.of(Map.of("k", "v")));
        when(repository.findByIdAndTenantId("ds-empty-page", "tenant-a")).thenReturn(Optional.of(dataset));

        DatasetDTO page = service.getDatasetPage("ds-empty-page", "tenant-a", 5, 10);

        assertEquals(0, page.getRows().size());
    }

    @Test
    void shouldThrowWhenDatasetNotFound() {
        when(repository.findByIdAndTenantId("missing", "tenant-a")).thenReturn(Optional.empty());

        BaseBusinessException ex = assertThrows(BaseBusinessException.class,
                () -> service.getSummary("missing", "tenant-a"));

        assertEquals(ErrorCode.DATASET_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void shouldDeleteDataset() {
        when(repository.existsByIdAndTenantId("ds-1", "tenant-a")).thenReturn(true);

        service.delete("ds-1", "tenant-a");

        verify(repository).deleteByIdAndTenantId("ds-1", "tenant-a");
    }

    @Test
    void shouldThrowWhenDeletingNonExistentDataset() {
        when(repository.existsByIdAndTenantId("gone", "tenant-a")).thenReturn(false);

        BaseBusinessException ex = assertThrows(BaseBusinessException.class,
                () -> service.delete("gone", "tenant-a"));

        assertEquals(ErrorCode.DATASET_NOT_FOUND, ex.getErrorCode());
    }

    private SaveDatasetRequestDTO buildRequest(String name, int rowCount) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            rows.add(Map.of("id", i));
        }
        DatasetDTO dataset = DatasetDTO.builder()
                .schema(DatasetSchemaDTO.builder().build())
                .stat(DatasetStatDTO.builder().rowCount(rowCount).build())
                .rows(rows)
                .build();
        return SaveDatasetRequestDTO.builder()
                .name(name)
                .description("test description")
                .dataset(dataset)
                .build();
    }

    private SavedDataset buildSavedDataset(String datasetId) {
        return SavedDataset.builder()
                .datasetId(datasetId)
                .tenantId("tenant-a")
                .name("Dataset " + datasetId)
                .createdAt(1000L)
                .updatedAt(2000L)
                .build();
    }

    private SavedDataset buildSavedDatasetWithRows(String datasetId, List<Map<String, Object>> rows) {
        return SavedDataset.builder()
                .datasetId(datasetId)
                .tenantId("tenant-a")
                .name("Dataset " + datasetId)
                .rows(rows)
                .createdAt(1000L)
                .updatedAt(2000L)
                .build();
    }
}
