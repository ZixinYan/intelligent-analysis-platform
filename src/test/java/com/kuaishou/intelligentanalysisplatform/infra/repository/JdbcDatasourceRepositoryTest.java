package com.kuaishou.intelligentanalysisplatform.infra.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceType;
import com.kuaishou.intelligentanalysisplatform.domain.datasource.AnalysisDatasource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JdbcTest
@Import({JdbcDatasourceRepository.class, ObjectMapper.class})
@Sql("classpath:schema.sql")
class JdbcDatasourceRepositoryTest {

    @Autowired
    private JdbcDatasourceRepository repository;

    @Test
    void shouldFilterByTenantAndPaginate() {
        repository.save(create("tenant-a", "a1"));
        repository.save(create("tenant-a", "a2"));
        repository.save(create("tenant-b", "b1"));

        var page = repository.findByTenant("tenant-a", null, null, 1, 1);
        assertEquals(2, page.getTotal());
        assertEquals(1, page.getItems().size());
    }

    @Test
    void shouldCheckNameExistsWithExcludeId() {
        AnalysisDatasource datasource = create("tenant-a", "demo");
        repository.save(datasource);
        assertTrue(repository.existsByName("tenant-a", "demo", null));
        assertFalse(repository.existsByName("tenant-a", "demo", datasource.getId()));
    }

    @Test
    void shouldDeleteByTenant() {
        AnalysisDatasource datasource = create("tenant-a", "demo");
        repository.save(datasource);
        repository.deleteByIdAndTenantId(datasource.getId(), "tenant-a");
        assertFalse(repository.findById(datasource.getId()).isPresent());
    }

    private AnalysisDatasource create(String tenantId, String name) {
        return AnalysisDatasource.create(tenantId, name, DatasourceType.MYSQL, "127.0.0.1", 3306,
                "analytics", "reader", "cipher", Map.of(), true, "user-a");
    }
}
