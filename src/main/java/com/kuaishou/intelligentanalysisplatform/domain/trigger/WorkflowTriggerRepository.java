package com.kuaishou.intelligentanalysisplatform.domain.trigger;

import java.util.List;
import java.util.Optional;

public interface WorkflowTriggerRepository {
    WorkflowTrigger save(WorkflowTrigger trigger);
    Optional<WorkflowTrigger> findById(String id);
    List<WorkflowTrigger> findByWorkflowId(String workflowId);
    Optional<WorkflowTrigger> findByWebhookToken(String webhookToken);
    void updateNextFireAt(String triggerId, Long nextFireAt, long lastFireAt);
    void updateStatus(String triggerId, TriggerStatus status, long updatedAt);
    void deleteById(String triggerId);

    /** Finds all SCHEDULE/ACTIVE triggers whose next_fire_at ≤ nowMs, capped at 50. */
    List<WorkflowTrigger> findDueCronTriggers(long nowMs);

    /** Updates last_fire_at, last_status, next_fire_at and updated_at in one SQL call. */
    void updateLastFire(String triggerId, long firedAt, String status, long nextFireAt);
}
