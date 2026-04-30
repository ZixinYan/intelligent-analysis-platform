package com.kuaishou.intelligentanalysisplatform.infra.observability;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.domain.query.execution.QueryExecution;
import com.kuaishou.intelligentanalysisplatform.domain.task.AsyncTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class QueryAuditLogService {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueryAuditLogService.class);

    private final ObjectMapper objectMapper;

    public QueryAuditLogService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void logQuery(QueryExecution execution, boolean slowQuery) {
        if (execution == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "query_execution_audit");
        payload.put("queryId", execution.getQueryId());
        payload.put("tenantId", execution.getTenantId());
        payload.put("operatorId", execution.getOperatorId());
        payload.put("datasourceId", execution.getDatasourceId());
        payload.put("sqlFingerprint", execution.getSqlFingerprint());
        payload.put("mode", execution.getMode());
        payload.put("status", toStatus(execution.getStatus()));
        payload.put("elapsedMs", execution.getElapsedMs());
        payload.put("rowCount", execution.getRowCount());
        payload.put("cached", execution.getCached());
        payload.put("truncated", execution.getTruncated());
        payload.put("errorCode", execution.getErrorCode());
        payload.put("slowQuery", slowQuery);
        log(slowQuery, payload);
    }

    public void logTask(AsyncTask task) {
        if (task == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "async_task_audit");
        payload.put("taskId", task.getTaskId());
        payload.put("taskType", task.getTaskType() == null ? null : task.getTaskType().name());
        payload.put("refId", task.getRefId());
        payload.put("tenantId", task.getTenantId());
        payload.put("operatorId", task.getOperatorId());
        payload.put("status", toStatus(task.getStatus()));
        payload.put("errorCode", task.getErrorCode());
        payload.put("updatedAt", task.getUpdatedAt());
        log(false, payload);
    }

    private void log(boolean warn, Map<String, Object> payload) {
        try {
            String message = objectMapper.writeValueAsString(payload);
            if (warn) {
                LOGGER.warn(message);
                return;
            }
            LOGGER.info(message);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("{\"event\":\"audit_log_serialize_failed\",\"message\":\"{}\"}", exception.getMessage());
        }
    }

    private String toStatus(ExecutionStatus status) {
        return status == null ? null : status.name();
    }
}
