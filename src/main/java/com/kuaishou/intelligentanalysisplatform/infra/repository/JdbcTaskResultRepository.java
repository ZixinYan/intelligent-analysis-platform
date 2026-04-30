package com.kuaishou.intelligentanalysisplatform.infra.repository;

import com.kuaishou.intelligentanalysisplatform.domain.task.TaskResult;
import com.kuaishou.intelligentanalysisplatform.domain.task.TaskResultRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcTaskResultRepository implements TaskResultRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcTaskResultRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(TaskResult result) {
        jdbcTemplate.update("""
                MERGE INTO task_result (task_id, result_json, created_at)
                KEY(task_id)
                VALUES (:taskId, :resultJson, :createdAt)
                """, new MapSqlParameterSource()
                .addValue("taskId", result.getTaskId())
                .addValue("resultJson", result.getResultJson())
                .addValue("createdAt", result.getCreatedAt()));
    }

    @Override
    public Optional<TaskResult> findById(String taskId) {
        return jdbcTemplate.query("""
                SELECT task_id, result_json, created_at
                FROM task_result
                WHERE task_id = :taskId
                """, Map.of("taskId", taskId), rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(TaskResult.builder()
                    .taskId(rs.getString("task_id"))
                    .resultJson(rs.getString("result_json"))
                    .createdAt(rs.getLong("created_at"))
                    .build());
        });
    }
}
