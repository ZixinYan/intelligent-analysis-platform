package com.kuaishou.intelligentanalysisplatform.infra.repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.kuaishou.intelligentanalysisplatform.common.response.PageResult;
import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceType;
import com.kuaishou.intelligentanalysisplatform.domain.datasource.AnalysisDatasource;
import com.kuaishou.intelligentanalysisplatform.domain.datasource.DatasourceRepository;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryDatasourceRepository implements DatasourceRepository {
    private final ConcurrentMap<String, AnalysisDatasource> store = new ConcurrentHashMap<>();

    @Override
    public Optional<AnalysisDatasource> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<AnalysisDatasource> findByIdAndTenantId(String id, String tenantId) {
        return findById(id).filter(item -> item.getTenantId().equals(tenantId));
    }

    @Override
    public PageResult<AnalysisDatasource> findByTenant(String tenantId, DatasourceType type, String keyword, int page, int pageSize) {
        List<AnalysisDatasource> items = store.values().stream()
                .filter(item -> item.getTenantId().equals(tenantId))
                .filter(item -> type == null || item.getType() == type)
                .filter(item -> matchesKeyword(item, keyword))
                .sorted(Comparator.comparing(AnalysisDatasource::getCreatedAt).reversed())
                .toList();
        int normalizedPage = Math.max(page, 1);
        int normalizedPageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((normalizedPage - 1) * normalizedPageSize, items.size());
        int toIndex = Math.min(fromIndex + normalizedPageSize, items.size());
        return PageResult.<AnalysisDatasource>builder()
                .items(new ArrayList<>(items.subList(fromIndex, toIndex)))
                .total(items.size())
                .page(normalizedPage)
                .pageSize(normalizedPageSize)
                .build();
    }

    @Override
    public AnalysisDatasource save(AnalysisDatasource datasource) {
        store.put(datasource.getId(), datasource);
        return datasource;
    }

    @Override
    public void deleteByIdAndTenantId(String id, String tenantId) {
        findByIdAndTenantId(id, tenantId).ifPresent(item -> store.remove(item.getId()));
    }

    @Override
    public boolean existsByIdAndTenantId(String id, String tenantId) {
        return findByIdAndTenantId(id, tenantId).isPresent();
    }

    @Override
    public boolean existsByName(String tenantId, String name, String excludeId) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return store.values().stream()
                .filter(item -> item.getTenantId().equals(tenantId))
                .filter(item -> excludeId == null || !item.getId().equals(excludeId))
                .anyMatch(item -> item.getName().equalsIgnoreCase(name));
    }

    private boolean matchesKeyword(AnalysisDatasource datasource, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.toLowerCase(Locale.ROOT);
        return datasource.getName().toLowerCase(Locale.ROOT).contains(normalized)
                || datasource.getHost().toLowerCase(Locale.ROOT).contains(normalized)
                || datasource.getDatabase().toLowerCase(Locale.ROOT).contains(normalized);
    }
}
