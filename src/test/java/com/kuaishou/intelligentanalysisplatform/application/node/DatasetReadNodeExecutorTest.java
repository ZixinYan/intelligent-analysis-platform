package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.kuaishou.intelligentanalysisplatform.application.DatasetApplicationService;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ResultKind;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetReadNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.ValidationResultDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatasetReadNodeExecutorTest {

    private final DatasetApplicationService datasetApplicationService = mock(DatasetApplicationService.class);
    private final DatasetReadNodeExecutor executor = new DatasetReadNodeExecutor(datasetApplicationService);

    @Test
    void shouldReturnDatasetSuccessfully() {
        DatasetDTO dataset = DatasetDTO.builder()
                .rows(List.of(Map.of("id", 1), Map.of("id", 2), Map.of("id", 3)))
                .build();
        when(datasetApplicationService.getFullDataset("ds-1", "tenant-a")).thenReturn(dataset);

        NodeResultDTO result = executor.execute(buildContext("node-1", "tenant-a"),
                DatasetReadNodeConfigDTO.builder().datasetId("ds-1").build());

        assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());
        assertEquals(ResultKind.DATASET, result.getResult().getKind());
        assertEquals(3, result.getResult().getDataset().getRows().size());
    }

    @Test
    void shouldTruncateRowsWhenRowLimitSet() {
        DatasetDTO dataset = DatasetDTO.builder()
                .rows(List.of(Map.of("id", 1), Map.of("id", 2), Map.of("id", 3), Map.of("id", 4)))
                .build();
        when(datasetApplicationService.getFullDataset("ds-2", "tenant-a")).thenReturn(dataset);

        NodeResultDTO result = executor.execute(buildContext("node-2", "tenant-a"),
                DatasetReadNodeConfigDTO.builder().datasetId("ds-2").rowLimit(2).build());

        assertEquals(2, result.getResult().getDataset().getRows().size());
    }

    @Test
    void shouldNotTruncateWhenRowsBelowLimit() {
        DatasetDTO dataset = DatasetDTO.builder()
                .rows(List.of(Map.of("id", 1)))
                .build();
        when(datasetApplicationService.getFullDataset("ds-3", "tenant-a")).thenReturn(dataset);

        NodeResultDTO result = executor.execute(buildContext("node-3", "tenant-a"),
                DatasetReadNodeConfigDTO.builder().datasetId("ds-3").rowLimit(10).build());

        assertEquals(1, result.getResult().getDataset().getRows().size());
    }

    @Test
    void shouldThrowWhenDatasetNotFound() {
        when(datasetApplicationService.getFullDataset("missing", "tenant-a"))
                .thenThrow(new BaseBusinessException(ErrorCode.DATASET_NOT_FOUND, "not found"));

        org.junit.jupiter.api.Assertions.assertThrows(BaseBusinessException.class,
                () -> executor.execute(buildContext("node-4", "tenant-a"),
                        DatasetReadNodeConfigDTO.builder().datasetId("missing").build()));
    }

    @Test
    void shouldSupportDatasetReadType() {
        assertEquals("dataset_read", executor.supportType());
    }

    @Test
    void shouldValidateSuccessfully() {
        ValidationResultDTO result = executor.validate(
                DatasetReadNodeConfigDTO.builder().datasetId("ds-1").build());
        assertTrue(result.isValid());
    }

    @Test
    void shouldFailValidationWhenDatasetIdMissing() {
        ValidationResultDTO result = executor.validate(
                DatasetReadNodeConfigDTO.builder().build());
        assertFalse(result.isValid());
        assertNotNull(result.getErrorMessage());
    }

    private NodeExecuteContextDTO buildContext(String nodeId, String tenantId) {
        return NodeExecuteContextDTO.builder()
                .workflowId("wf-1")
                .runId("run-1")
                .nodeId(nodeId)
                .requestContext(RequestContextDTO.builder().tenantId(tenantId).build())
                .build();
    }
}
