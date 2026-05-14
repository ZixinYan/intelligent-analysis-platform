package com.kuaishou.intelligentanalysisplatform.domain.knowledge;

import java.util.List;
import java.util.Optional;

public interface KnowledgeBaseRepository {
    KnowledgeBase save(KnowledgeBase kb);
    Optional<KnowledgeBase> findById(String id);
    List<KnowledgeBase> findByTenantId(String tenantId);
    void deleteById(String id);
}
