package com.kuaishou.intelligentanalysisplatform.domain.workflow;

import java.util.List;
import java.util.Optional;

public interface WorkflowDefinitionRepository {
    void save(WorkflowDefinition definition);

    void update(WorkflowDefinition definition);

    /** 更新 current_version_id / published_version_id，不触碰 definition_json */
    void updateVersionRef(WorkflowDefinition definition);

    /** 同时更新 definition_json 和版本引用（用于回滚） */
    void updateDefinitionAndVersionRef(WorkflowDefinition definition);

    Optional<WorkflowDefinition> findByIdAndTenantId(String workflowId, String tenantId);

    long countByTenantId(String tenantId);

    List<WorkflowDefinition> findByTenantId(String tenantId, int offset, int limit);
}
