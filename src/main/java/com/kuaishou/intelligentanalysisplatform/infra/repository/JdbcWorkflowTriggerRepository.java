package com.kuaishou.intelligentanalysisplatform.infra.repository;

import java.util.List;
import java.util.Optional;

import com.kuaishou.intelligentanalysisplatform.domain.trigger.TriggerStatus;
import com.kuaishou.intelligentanalysisplatform.domain.trigger.TriggerType;
import com.kuaishou.intelligentanalysisplatform.domain.trigger.WorkflowTrigger;
import com.kuaishou.intelligentanalysisplatform.domain.trigger.WorkflowTriggerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcWorkflowTriggerRepository implements WorkflowTriggerRepository {

    private final JdbcTemplate jdbc;

    private static final RowMapper<WorkflowTrigger> ROW_MAPPER = (rs, rowNum) -> WorkflowTrigger.builder()
            .id(rs.getString("id"))
            .workflowId(rs.getString("workflow_id"))
            .tenantId(rs.getString("tenant_id"))
            .triggerType(TriggerType.valueOf(rs.getString("trigger_type")))
            .triggerStatus(TriggerStatus.valueOf(rs.getString("trigger_status")))
            .cronExpr(rs.getString("cron_expr"))
            .nextFireAt(rs.getObject("next_fire_at", Long.class))
            .webhookToken(rs.getString("webhook_token"))
            .secretKey(rs.getString("secret_key"))
            .defaultInputs(rs.getString("default_inputs"))
            .lastFireAt(rs.getObject("last_fire_at", Long.class))
            .lastRunId(rs.getString("last_run_id"))
            .lastStatus(rs.getString("last_status"))
            .createdAt(rs.getLong("created_at"))
            .updatedAt(rs.getLong("updated_at"))
            .build();

    @Override
    public WorkflowTrigger save(WorkflowTrigger t) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_trigger WHERE id = ?", Integer.class, t.getId());
        if (count != null && count > 0) {
            jdbc.update("""
                    UPDATE workflow_trigger SET
                        trigger_status = ?, next_fire_at = ?, last_fire_at = ?,
                        last_run_id = ?, last_status = ?, updated_at = ?
                    WHERE id = ?
                    """,
                    t.getTriggerStatus().name(), t.getNextFireAt(), t.getLastFireAt(),
                    t.getLastRunId(), t.getLastStatus(), t.getUpdatedAt(), t.getId());
        } else {
            jdbc.update("""
                    INSERT INTO workflow_trigger (
                        id, workflow_id, tenant_id, trigger_type, trigger_status,
                        cron_expr, next_fire_at, webhook_token, secret_key,
                        default_inputs, last_fire_at, last_run_id, last_status,
                        created_at, updated_at
                    ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    t.getId(), t.getWorkflowId(), t.getTenantId(),
                    t.getTriggerType().name(), t.getTriggerStatus().name(),
                    t.getCronExpr(), t.getNextFireAt(),
                    t.getWebhookToken(), t.getSecretKey(),
                    t.getDefaultInputs(),
                    t.getLastFireAt(), t.getLastRunId(), t.getLastStatus(),
                    t.getCreatedAt(), t.getUpdatedAt());
        }
        return t;
    }

    @Override
    public Optional<WorkflowTrigger> findById(String id) {
        List<WorkflowTrigger> list = jdbc.query(
                "SELECT * FROM workflow_trigger WHERE id = ?", ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<WorkflowTrigger> findByWorkflowId(String workflowId) {
        return jdbc.query(
                "SELECT * FROM workflow_trigger WHERE workflow_id = ? ORDER BY created_at DESC",
                ROW_MAPPER, workflowId);
    }

    @Override
    public Optional<WorkflowTrigger> findByWebhookToken(String webhookToken) {
        List<WorkflowTrigger> list = jdbc.query(
                "SELECT * FROM workflow_trigger WHERE webhook_token = ?", ROW_MAPPER, webhookToken);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public void updateNextFireAt(String triggerId, Long nextFireAt, long lastFireAt) {
        jdbc.update(
                "UPDATE workflow_trigger SET next_fire_at = ?, last_fire_at = ?, updated_at = ? WHERE id = ?",
                nextFireAt, lastFireAt, System.currentTimeMillis(), triggerId);
    }

    @Override
    public void updateStatus(String triggerId, TriggerStatus status, long updatedAt) {
        jdbc.update(
                "UPDATE workflow_trigger SET trigger_status = ?, updated_at = ? WHERE id = ?",
                status.name(), updatedAt, triggerId);
    }

    @Override
    public void deleteById(String triggerId) {
        jdbc.update("DELETE FROM workflow_trigger WHERE id = ?", triggerId);
    }

    @Override
    public List<WorkflowTrigger> findDueCronTriggers(long nowMs) {
        return jdbc.query(
                "SELECT * FROM workflow_trigger WHERE trigger_type = ? AND trigger_status = ? AND next_fire_at <= ? ORDER BY next_fire_at ASC LIMIT 50",
                ROW_MAPPER, TriggerType.SCHEDULE.name(), TriggerStatus.ACTIVE.name(), nowMs);
    }

    @Override
    public void updateLastFire(String triggerId, long firedAt, String status, long nextFireAt) {
        jdbc.update(
                "UPDATE workflow_trigger SET last_fire_at = ?, last_status = ?, next_fire_at = ?, updated_at = ? WHERE id = ?",
                firedAt, status, nextFireAt, firedAt, triggerId);
    }
}

