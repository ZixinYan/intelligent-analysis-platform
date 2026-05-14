package com.kuaishou.intelligentanalysisplatform.infra.repository;

import java.util.List;
import java.util.Optional;

import com.kuaishou.intelligentanalysisplatform.domain.knowledge.KnowledgeBase;
import com.kuaishou.intelligentanalysisplatform.domain.knowledge.KnowledgeBaseRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcKnowledgeBaseRepository implements KnowledgeBaseRepository {

    private final JdbcTemplate jdbc;

    public JdbcKnowledgeBaseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public KnowledgeBase save(KnowledgeBase kb) {
        jdbc.update("""
                INSERT INTO knowledge_base (id, tenant_id, name, description, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    name = VALUES(name),
                    description = VALUES(description),
                    updated_at = VALUES(updated_at)
                """,
                kb.getId(), kb.getTenantId(), kb.getName(),
                kb.getDescription(), kb.getCreatedAt(), kb.getUpdatedAt());
        return kb;
    }

    @Override
    public Optional<KnowledgeBase> findById(String id) {
        List<KnowledgeBase> list = jdbc.query(
                "SELECT id, tenant_id, name, description, created_at, updated_at FROM knowledge_base WHERE id = ?",
                (rs, rowNum) -> KnowledgeBase.builder()
                        .id(rs.getString("id"))
                        .tenantId(rs.getString("tenant_id"))
                        .name(rs.getString("name"))
                        .description(rs.getString("description"))
                        .createdAt(rs.getLong("created_at"))
                        .updatedAt(rs.getLong("updated_at"))
                        .build(),
                id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<KnowledgeBase> findByTenantId(String tenantId) {
        return jdbc.query(
                "SELECT id, tenant_id, name, description, created_at, updated_at FROM knowledge_base WHERE tenant_id = ? ORDER BY updated_at DESC",
                (rs, rowNum) -> KnowledgeBase.builder()
                        .id(rs.getString("id"))
                        .tenantId(rs.getString("tenant_id"))
                        .name(rs.getString("name"))
                        .description(rs.getString("description"))
                        .createdAt(rs.getLong("created_at"))
                        .updatedAt(rs.getLong("updated_at"))
                        .build(),
                tenantId);
    }

    @Override
    public void deleteById(String id) {
        jdbc.update("DELETE FROM knowledge_base WHERE id = ?", id);
    }
}
