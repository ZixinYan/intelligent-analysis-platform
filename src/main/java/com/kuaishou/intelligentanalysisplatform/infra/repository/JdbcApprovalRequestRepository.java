package com.kuaishou.intelligentanalysisplatform.infra.repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ApprovalStatus;
import com.kuaishou.intelligentanalysisplatform.domain.approval.ApprovalRequest;
import com.kuaishou.intelligentanalysisplatform.domain.approval.ApprovalRequestRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcApprovalRequestRepository implements ApprovalRequestRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcApprovalRequestRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(ApprovalRequest request) {
        jdbcTemplate.update("""
                INSERT INTO approval_request (
                    request_id, workflow_id, node_id, tenant_id, reason,
                    approvers_json, status, decided_by, decision_comment,
                    created_at, decided_at, expires_at
                ) VALUES (
                    :requestId, :workflowId, :nodeId, :tenantId, :reason,
                    :approversJson, :status, :decidedBy, :decisionComment,
                    :createdAt, :decidedAt, :expiresAt
                )
                """, toParams(request));
    }

    @Override
    public void updateDecision(String requestId, ApprovalStatus status,
                               String decidedBy, String comment, Long decidedAt) {
        jdbcTemplate.update("""
                UPDATE approval_request
                SET status = :status,
                    decided_by = :decidedBy,
                    decision_comment = :decisionComment,
                    decided_at = :decidedAt
                WHERE request_id = :requestId
                  AND status = 'PENDING'
                """, new MapSqlParameterSource()
                .addValue("requestId", requestId)
                .addValue("status", status.name())
                .addValue("decidedBy", decidedBy)
                .addValue("decisionComment", comment)
                .addValue("decidedAt", decidedAt));
    }

    @Override
    public Optional<ApprovalRequest> findById(String requestId) {
        return jdbcTemplate.query("""
                SELECT request_id, workflow_id, node_id, tenant_id, reason,
                       approvers_json, status, decided_by, decision_comment,
                       created_at, decided_at, expires_at
                FROM approval_request
                WHERE request_id = :requestId
                """, Map.of("requestId", requestId), rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(toApprovalRequest(rs));
        });
    }

    @Override
    public List<ApprovalRequest> findPendingByWorkflowAndNode(String workflowId, String nodeId) {
        return jdbcTemplate.query("""
                SELECT request_id, workflow_id, node_id, tenant_id, reason,
                       approvers_json, status, decided_by, decision_comment,
                       created_at, decided_at, expires_at
                FROM approval_request
                WHERE workflow_id = :workflowId
                  AND node_id = :nodeId
                  AND status = 'PENDING'
                """, new MapSqlParameterSource()
                .addValue("workflowId", workflowId)
                .addValue("nodeId", nodeId),
                (rs, rowNum) -> toApprovalRequest(rs));
    }

    private ApprovalRequest toApprovalRequest(java.sql.ResultSet rs) throws java.sql.SQLException {
        return ApprovalRequest.builder()
                .requestId(rs.getString("request_id"))
                .workflowId(rs.getString("workflow_id"))
                .nodeId(rs.getString("node_id"))
                .tenantId(rs.getString("tenant_id"))
                .reason(rs.getString("reason"))
                .approvers(parseApprovers(rs.getString("approvers_json")))
                .status(ApprovalStatus.valueOf(rs.getString("status")))
                .decidedBy(rs.getString("decided_by"))
                .decisionComment(rs.getString("decision_comment"))
                .createdAt(rs.getLong("created_at"))
                .decidedAt(rs.getObject("decided_at", Long.class))
                .expiresAt(rs.getObject("expires_at", Long.class))
                .build();
    }

    private MapSqlParameterSource toParams(ApprovalRequest request) {
        return new MapSqlParameterSource()
                .addValue("requestId", request.getRequestId())
                .addValue("workflowId", request.getWorkflowId())
                .addValue("nodeId", request.getNodeId())
                .addValue("tenantId", request.getTenantId())
                .addValue("reason", request.getReason())
                .addValue("approversJson", serializeApprovers(request.getApprovers()))
                .addValue("status", request.getStatus() == null ? null : request.getStatus().name())
                .addValue("decidedBy", request.getDecidedBy())
                .addValue("decisionComment", request.getDecisionComment())
                .addValue("createdAt", request.getCreatedAt())
                .addValue("decidedAt", request.getDecidedAt())
                .addValue("expiresAt", request.getExpiresAt());
    }

    private String serializeApprovers(List<String> approvers) {
        if (approvers == null || approvers.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(approvers);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize approvers", e);
        }
    }

    private List<String> parseApprovers(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }
}
