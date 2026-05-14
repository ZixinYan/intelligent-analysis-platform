package com.kuaishou.intelligentanalysisplatform.domain.trigger;

import java.util.List;
import java.util.Optional;

public interface WorkflowTriggerRepository {
    WorkflowTrigger save(WorkflowTrigger trigger);
    Optional<WorkflowTrigger> findById(String id);
    List<WorkflowTrigger> findByWorkflowId(String workflowId);
    Optional<WorkflowTrigger> findByWebhookToken(String webhookToken);
    List<WorkflowTrigger> findDueScheduleTriggers(TriggerType type, TriggerStatus status, long now);
    void updateNextFireAt(String triggerId, Long nextFireAt, long lastFireAt);
    void updateStatus(String triggerId, TriggerStatus status, long updatedAt);
    void deleteById(String triggerId);
}
