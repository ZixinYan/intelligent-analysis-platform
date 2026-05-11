package com.kuaishou.intelligentanalysisplatform.infra.repository;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ApprovalStatus;
import com.kuaishou.intelligentanalysisplatform.domain.approval.ApprovalRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JdbcTest
@Import({JdbcApprovalRequestRepositoryTest.TestConfig.class, JdbcApprovalRequestRepository.class})
@Sql("classpath:schema.sql")
class JdbcApprovalRequestRepositoryTest {

    @Autowired
    private JdbcApprovalRequestRepository repository;

    @Test
    void shouldSaveAndFindById() {
        repository.save(buildRequest("req-001"));

        ApprovalRequest found = repository.findById("req-001").orElseThrow();
        assertEquals("req-001", found.getRequestId());
        assertEquals("wf-1", found.getWorkflowId());
        assertEquals("node-1", found.getNodeId());
        assertEquals("tenant-a", found.getTenantId());
        assertEquals(ApprovalStatus.PENDING, found.getStatus());
        assertEquals(List.of("user-approver"), found.getApprovers());
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
        assertTrue(repository.findById("nonexistent").isEmpty());
    }

    @Test
    void shouldUpdateDecision() {
        repository.save(buildRequest("req-002"));

        repository.updateDecision("req-002", ApprovalStatus.APPROVED, "reviewer-1", "ok", 2000L);

        ApprovalRequest updated = repository.findById("req-002").orElseThrow();
        assertEquals(ApprovalStatus.APPROVED, updated.getStatus());
        assertEquals("reviewer-1", updated.getDecidedBy());
        assertEquals("ok", updated.getDecisionComment());
        assertEquals(2000L, updated.getDecidedAt());
    }

    @Test
    void shouldNotUpdateDecisionIfAlreadyDecided() {
        repository.save(buildRequest("req-003"));
        repository.updateDecision("req-003", ApprovalStatus.APPROVED, "u1", "ok", 1000L);

        // Second update must not change the status (AND status = 'PENDING' guard)
        repository.updateDecision("req-003", ApprovalStatus.REJECTED, "u2", "override", 2000L);

        ApprovalRequest found = repository.findById("req-003").orElseThrow();
        assertEquals(ApprovalStatus.APPROVED, found.getStatus());
        assertEquals("u1", found.getDecidedBy());
    }

    @Test
    void shouldFindPendingByWorkflowAndNode() {
        repository.save(buildRequest("req-004"));
        repository.save(ApprovalRequest.builder()
                .requestId("req-005")
                .workflowId("wf-1")
                .nodeId("other-node")
                .tenantId("tenant-a")
                .approvers(List.of("user-approver"))
                .status(ApprovalStatus.PENDING)
                .createdAt(1000L)
                .build());

        List<ApprovalRequest> pending = repository.findPendingByWorkflowAndNode("wf-1", "node-1");

        assertTrue(pending.stream().anyMatch(r -> "req-004".equals(r.getRequestId())));
        assertFalse(pending.stream().anyMatch(r -> "req-005".equals(r.getRequestId())));
    }

    private ApprovalRequest buildRequest(String requestId) {
        return ApprovalRequest.builder()
                .requestId(requestId)
                .workflowId("wf-1")
                .nodeId("node-1")
                .tenantId("tenant-a")
                .reason("needs review")
                .approvers(List.of("user-approver"))
                .status(ApprovalStatus.PENDING)
                .createdAt(1000L)
                .build();
    }

    @Configuration
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
