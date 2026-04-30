package com.kuaishou.intelligentanalysisplatform.domain.datasource;

import java.util.Optional;

import com.kuaishou.intelligentanalysisplatform.common.response.PageResult;
import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceType;

public interface DatasourceRepository {
    Optional<AnalysisDatasource> findById(String id);

    Optional<AnalysisDatasource> findByIdAndTenantId(String id, String tenantId);

    PageResult<AnalysisDatasource> findByTenant(String tenantId, DatasourceType type, String keyword, int page, int pageSize);

    AnalysisDatasource save(AnalysisDatasource datasource);

    void deleteByIdAndTenantId(String id, String tenantId);

    boolean existsByIdAndTenantId(String id, String tenantId);

    boolean existsByName(String tenantId, String name, String excludeId);
}
