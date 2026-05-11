package com.kuaishou.intelligentanalysisplatform.domain.dataset;

import java.util.List;
import java.util.Optional;

public interface SavedDatasetRepository {
    void save(SavedDataset dataset);
    void update(SavedDataset dataset);
    Optional<SavedDataset> findById(String datasetId);
    Optional<SavedDataset> findByIdAndTenantId(String datasetId, String tenantId);
    /** 仅返回 summary 信息（不含 rows），用于列表接口 */
    List<SavedDataset> findSummaryByTenantId(String tenantId, int limit, long beforeUpdatedAt);
    void deleteByIdAndTenantId(String datasetId, String tenantId);
    boolean existsByIdAndTenantId(String datasetId, String tenantId);
}
