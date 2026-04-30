package com.kuaishou.intelligentanalysisplatform.infra.repository;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.domain.task.AsyncTask;
import com.kuaishou.intelligentanalysisplatform.domain.task.AsyncTaskRepository;
import com.kuaishou.intelligentanalysisplatform.domain.task.TaskType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcAsyncTaskRepository implements AsyncTaskRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcAsyncTaskRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(AsyncTask task) {
        jdbcTemplate.update("""
                INSERT INTO async_task (
                    task_id, task_type, ref_id, tenant_id, operator_id,
                    status, error_code, error_message, created_at, updated_at
                ) VALUES (
                    :taskId, :taskType, :refId, :tenantId, :operatorId,
                    :status, :errorCode, :errorMessage, :createdAt, :updatedAt
                )
                """, toParams(task));
    }

    @Override
    public void updateStatus(String taskId, ExecutionStatus status, Long updatedAt, String errorCode, String errorMessage) {
        jdbcTemplate.update("""
                UPDATE async_task
                SET status = :status,
                    updated_at = :updatedAt,
                    error_code = :errorCode,
                    error_message = :errorMessage
                WHERE task_id = :taskId
                """, new MapSqlParameterSource()
                .addValue("taskId", taskId)
                .addValue("status", status == null ? null : status.name())
                .addValue("updatedAt", updatedAt)
                .addValue("errorCode", errorCode)
                .addValue("errorMessage", errorMessage));
    }

    @Override
    public Optional<AsyncTask> findById(String taskId) {
        return jdbcTemplate.query("""
                SELECT task_id, task_type, ref_id, tenant_id, operator_id,
                       status, error_code, error_message, created_at, updated_at
                FROM async_task
                WHERE task_id = :taskId
                """, Map.of("taskId", taskId), rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(AsyncTask.builder()
                    .taskId(rs.getString("task_id"))
                    .taskType(TaskType.valueOf(rs.getString("task_type")))
                    .refId(rs.getString("ref_id"))
                    .tenantId(rs.getString("tenant_id"))
                    .operatorId(rs.getString("operator_id"))
                    .status(ExecutionStatus.valueOf(rs.getString("status")))
                    .errorCode(rs.getString("error_code"))
                    .errorMessage(rs.getString("error_message"))
                    .createdAt(rs.getLong("created_at"))
                    .updatedAt(rs.getLong("updated_at"))
                    .build());
        });
    }

    private MapSqlParameterSource toParams(AsyncTask task) {
        return new MapSqlParameterSource()
                .addValue("taskId", task.getTaskId())
                .addValue("taskType", task.getTaskType() == null ? null : task.getTaskType().name())
                .addValue("refId", task.getRefId())
                .addValue("tenantId", task.getTenantId())
                .addValue("operatorId", task.getOperatorId())
                .addValue("status", task.getStatus() == null ? null : task.getStatus().name())
                .addValue("errorCode", task.getErrorCode())
                .addValue("errorMessage", task.getErrorMessage())
                .addValue("createdAt", task.getCreatedAt())
                .addValue("updatedAt", task.getUpdatedAt());
    }
}
