package com.kuaishou.intelligentanalysisplatform.domain.workflow;

import java.util.List;
import java.util.Optional;

public interface WorkflowDefinitionRepository {
    void save(WorkflowDefinition definition);

    void update(WorkflowDefinition definition);

    Optional<WorkflowDefinition> findByIdAndTenantId(String workflowId, String tenantId);

    long countByTenantId(String tenantId);

    List<WorkflowDefinition> findByTenantId(String tenantId, int offset, int limit);
}
