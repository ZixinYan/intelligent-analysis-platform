package com.kuaishou.intelligentanalysisplatform.application;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SaveDatasetRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SavedDatasetSummaryDTO;

public interface DatasetApplicationService {
    SavedDatasetSummaryDTO save(SaveDatasetRequestDTO request, String tenantId, String operatorId);
    SavedDatasetSummaryDTO getSummary(String datasetId, String tenantId);
    DatasetDTO getFullDataset(String datasetId, String tenantId);
    DatasetDTO getDatasetPage(String datasetId, String tenantId, int page, int pageSize);
    List<SavedDatasetSummaryDTO> list(String tenantId, int limit, Long beforeUpdatedAt);
    void delete(String datasetId, String tenantId);
    void updateMeta(String datasetId, String tenantId, String name, String description);
}
