package com.kuaishou.intelligentanalysisplatform.application.impl;

import java.util.List;
import java.util.Optional;

import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ApprovalStatus;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ApprovalRequestDTO;
import com.kuaishou.intelligentanalysisplatform.domain.approval.ApprovalRequest;
import com.kuaishou.intelligentanalysisplatform.domain.approval.ApprovalRequestRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultApprovalApplicationServiceTest {

    private final ApprovalRequestRepository repository = mock(ApprovalRequestRepository.class);
    private final DefaultApprovalApplicationService service = new DefaultApprovalApplicationService(repository);

    @Test
    void shouldApproveWhenPending() {
        when(repository.findById("req-1")).thenReturn(Optional.of(pendingRequest("req-1")));

        service.approve("req-1", "tenant-a", "user-1", "looks good");

        verify(repository).updateDecision(eq("req-1"), eq(ApprovalStatus.APPROVED),
                eq("user-1"), eq("looks good"), any(Long.class));
    }

    @Test
    void shouldRejectWhenPending() {
        when(repository.findById("req-2")).thenReturn(Optional.of(pendingRequest("req-2")));

        service.reject("req-2", "tenant-a", "user-1", "denied");

        verify(repository).updateDecision(eq("req-2"), eq(ApprovalStatus.REJECTED),
                eq("user-1"), eq("denied"), any(Long.class));
    }

    @Test
    void shouldThrowWhenApproveAlreadyDecided() {
        ApprovalRequest decided = ApprovalRequest.builder()
                .requestId("req-3")
                .tenantId("tenant-a")
                .status(ApprovalStatus.APPROVED)
                .build();
        when(repository.findById("req-3")).thenReturn(Optional.of(decided));

        assertThrows(BaseBusinessException.class,
                () -> service.approve("req-3", "tenant-a", "user-1", "again"));
    }

    @Test
    void shouldThrowWhenRejectAlreadyDecided() {
        ApprovalRequest decided = ApprovalRequest.builder()
                .requestId("req-4")
                .tenantId("tenant-a")
                .status(ApprovalStatus.REJECTED)
                .build();
        when(repository.findById("req-4")).thenReturn(Optional.of(decided));

        assertThrows(BaseBusinessException.class,
                () -> service.reject("req-4", "tenant-a", "user-1", "again"));
    }

    @Test
    void shouldThrowWhenRequestNotFound() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(BaseBusinessException.class,
                () -> service.approve("missing", "tenant-a", "user-1", ""));
    }

    @Test
    void shouldThrowWhenUnauthorizedTenant() {
        when(repository.findById("req-5")).thenReturn(Optional.of(pendingRequest("req-5")));

        assertThrows(BaseBusinessException.class,
                () -> service.approve("req-5", "tenant-other", "user-1", ""));
    }

    @Test
    void shouldGetRequest() {
        when(repository.findById("req-6")).thenReturn(Optional.of(pendingRequest("req-6")));

        ApprovalRequestDTO dto = service.getRequest("req-6", "tenant-a");

        assertEquals("req-6", dto.getRequestId());
        assertEquals("PENDING", dto.getStatus());
    }

    @Test
    void shouldListPendingByWorkflowAndNode() {
        when(repository.findPendingByWorkflowAndNode("wf-1", "node-1"))
                .thenReturn(List.of(pendingRequest("req-7")));

        List<ApprovalRequestDTO> list = service.listPendingByWorkflowAndNode("wf-1", "node-1", "tenant-a");

        assertEquals(1, list.size());
        assertEquals("req-7", list.get(0).getRequestId());
    }

    private ApprovalRequest pendingRequest(String requestId) {
        return ApprovalRequest.builder()
                .requestId(requestId)
                .workflowId("wf-1")
                .nodeId("node-1")
                .tenantId("tenant-a")
                .status(ApprovalStatus.PENDING)
                .createdAt(System.currentTimeMillis())
                .build();
    }
}
