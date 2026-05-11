package com.kuaishou.intelligentanalysisplatform.application.impl;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.kuaishou.intelligentanalysisplatform.application.DatasetApplicationService;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SaveDatasetRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SavedDatasetSummaryDTO;
import com.kuaishou.intelligentanalysisplatform.domain.dataset.SavedDataset;
import com.kuaishou.intelligentanalysisplatform.domain.dataset.SavedDatasetRepository;
import org.springframework.stereotype.Service;

@Service
public class DefaultDatasetApplicationService implements DatasetApplicationService {

    private static final int MAX_ROWS = 100_000;

    private final SavedDatasetRepository savedDatasetRepository;

    public DefaultDatasetApplicationService(SavedDatasetRepository savedDatasetRepository) {
        this.savedDatasetRepository = savedDatasetRepository;
    }

    @Override
    public SavedDatasetSummaryDTO save(SaveDatasetRequestDTO request, String tenantId, String operatorId) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "Dataset name is required");
        }
        if (request.getDataset() == null) {
            throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "Dataset is required");
        }
        List<?> rows = request.getDataset().getRows();
        if (rows != null && rows.size() > MAX_ROWS) {
            throw new BaseBusinessException(ErrorCode.DATASET_TOO_LARGE,
                    "Dataset exceeds maximum row limit of " + MAX_ROWS + ", actual: " + rows.size());
        }
        long now = System.currentTimeMillis();
        SavedDataset entity = SavedDataset.builder()
                .datasetId(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .name(request.getName())
                .description(request.getDescription())
                .createdBy(operatorId)
                .schema(request.getDataset().getSchema())
                .stat(request.getDataset().getStat())
                .rows(request.getDataset().getRows())
                .sourceWorkflowId(request.getSourceWorkflowId())
                .sourceNodeId(request.getSourceNodeId())
                .createdAt(now)
                .updatedAt(now)
                .build();
        savedDatasetRepository.save(entity);
        return toSummary(entity);
    }

    @Override
    public SavedDatasetSummaryDTO getSummary(String datasetId, String tenantId) {
        SavedDataset dataset = findByIdAndTenant(datasetId, tenantId);
        return toSummary(dataset);
    }

    @Override
    public DatasetDTO getFullDataset(String datasetId, String tenantId) {
        SavedDataset dataset = findByIdAndTenant(datasetId, tenantId);
        return toDatasetDTO(dataset);
    }

    @Override
    public DatasetDTO getDatasetPage(String datasetId, String tenantId, int page, int pageSize) {
        SavedDataset dataset = findByIdAndTenant(datasetId, tenantId);
        List<java.util.Map<String, Object>> allRows = dataset.getRows();
        if (allRows == null) {
            allRows = List.of();
        }
        int fromIndex = page * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, allRows.size());
        List<java.util.Map<String, Object>> pageRows = fromIndex >= allRows.size()
                ? List.of()
                : allRows.subList(fromIndex, toIndex);
        return DatasetDTO.builder()
                .schema(dataset.getSchema())
                .stat(dataset.getStat())
                .rows(pageRows)
                .build();
    }

    @Override
    public List<SavedDatasetSummaryDTO> list(String tenantId, int limit, Long beforeUpdatedAt) {
        long cursor = beforeUpdatedAt != null ? beforeUpdatedAt : Long.MAX_VALUE;
        return savedDatasetRepository.findSummaryByTenantId(tenantId, limit, cursor)
                .stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String datasetId, String tenantId) {
        if (!savedDatasetRepository.existsByIdAndTenantId(datasetId, tenantId)) {
            throw new BaseBusinessException(ErrorCode.DATASET_NOT_FOUND, "Dataset not found: " + datasetId);
        }
        savedDatasetRepository.deleteByIdAndTenantId(datasetId, tenantId);
    }

    @Override
    public void updateMeta(String datasetId, String tenantId, String name, String description) {
        SavedDataset existing = findByIdAndTenant(datasetId, tenantId);
        if (name != null && !name.isBlank()) {
            existing.setName(name);
        }
        existing.setDescription(description);
        existing.setUpdatedAt(System.currentTimeMillis());
        savedDatasetRepository.update(existing);
    }

    private SavedDataset findByIdAndTenant(String datasetId, String tenantId) {
        return savedDatasetRepository.findByIdAndTenantId(datasetId, tenantId)
                .orElseThrow(() -> new BaseBusinessException(ErrorCode.DATASET_NOT_FOUND,
                        "Dataset not found: " + datasetId));
    }

    private SavedDatasetSummaryDTO toSummary(SavedDataset dataset) {
        return SavedDatasetSummaryDTO.builder()
                .datasetId(dataset.getDatasetId())
                .name(dataset.getName())
                .description(dataset.getDescription())
                .tenantId(dataset.getTenantId())
                .createdBy(dataset.getCreatedBy())
                .schema(dataset.getSchema())
                .stat(dataset.getStat())
                .sourceWorkflowId(dataset.getSourceWorkflowId())
                .sourceNodeId(dataset.getSourceNodeId())
                .createdAt(dataset.getCreatedAt())
                .updatedAt(dataset.getUpdatedAt())
                .build();
    }

    private DatasetDTO toDatasetDTO(SavedDataset dataset) {
        return DatasetDTO.builder()
                .schema(dataset.getSchema())
                .stat(dataset.getStat())
                .rows(dataset.getRows())
                .build();
    }
}
