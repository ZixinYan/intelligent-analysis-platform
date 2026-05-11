package com.kuaishou.intelligentanalysisplatform.domain.export;

import java.util.Optional;

public interface ExportFileRepository {
    void save(ExportFile file);
    Optional<ExportFile> findById(String fileId);
    Optional<ExportFile> findByIdAndTenantId(String fileId, String tenantId);
    void deleteExpired(long beforeTimestamp);
}
