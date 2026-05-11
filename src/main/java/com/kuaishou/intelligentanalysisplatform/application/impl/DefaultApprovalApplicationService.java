package com.kuaishou.intelligentanalysisplatform.application.impl;

import java.util.List;
import java.util.stream.Collectors;

import com.kuaishou.intelligentanalysisplatform.application.ApprovalApplicationService;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ApprovalStatus;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ApprovalRequestDTO;
import com.kuaishou.intelligentanalysisplatform.domain.approval.ApprovalRequest;
import com.kuaishou.intelligentanalysisplatform.domain.approval.ApprovalRequestRepository;
import org.springframework.stereotype.Service;

@Service
public class DefaultApprovalApplicationService implements ApprovalApplicationService {

    private final ApprovalRequestRepository approvalRequestRepository;

    public DefaultApprovalApplicationService(ApprovalRequestRepository approvalRequestRepository) {
        this.approvalRequestRepository = approvalRequestRepository;
    }

    @Override
    public ApprovalRequestDTO getRequest(String requestId, String tenantId) {
        ApprovalRequest request = findRequest(requestId);
        checkTenant(request, tenantId);
        return toDto(request);
    }

    @Override
    public void approve(String requestId, String tenantId, String decidedBy, String comment) {
        ApprovalRequest request = findRequest(requestId);
        checkTenant(request, tenantId);
        checkPending(request);
        long now = System.currentTimeMillis();
        approvalRequestRepository.updateDecision(requestId, ApprovalStatus.APPROVED, decidedBy, comment, now);
    }

    @Override
    public void reject(String requestId, String tenantId, String decidedBy, String comment) {
        ApprovalRequest request = findRequest(requestId);
        checkTenant(request, tenantId);
        checkPending(request);
        long now = System.currentTimeMillis();
        approvalRequestRepository.updateDecision(requestId, ApprovalStatus.REJECTED, decidedBy, comment, now);
    }

    @Override
    public List<ApprovalRequestDTO> listPendingByWorkflowAndNode(String workflowId, String nodeId, String tenantId) {
        return approvalRequestRepository.findPendingByWorkflowAndNode(workflowId, nodeId)
                .stream()
                .filter(r -> tenantId == null || tenantId.equals(r.getTenantId()))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private ApprovalRequest findRequest(String requestId) {
        return approvalRequestRepository.findById(requestId)
                .orElseThrow(() -> new BaseBusinessException(ErrorCode.APPROVAL_NOT_FOUND,
                        "Approval request not found: " + requestId));
    }

    private void checkTenant(ApprovalRequest request, String tenantId) {
        if (tenantId != null && !tenantId.equals(request.getTenantId())) {
            throw new BaseBusinessException(ErrorCode.APPROVAL_UNAUTHORIZED,
                    "Access denied for approval request: " + request.getRequestId());
        }
    }

    private void checkPending(ApprovalRequest request) {
        if (!request.isPending()) {
            throw new BaseBusinessException(ErrorCode.APPROVAL_ALREADY_DECIDED,
                    "Approval request already decided: " + request.getRequestId());
        }
    }

    private ApprovalRequestDTO toDto(ApprovalRequest request) {
        return ApprovalRequestDTO.builder()
                .requestId(request.getRequestId())
                .workflowId(request.getWorkflowId())
                .nodeId(request.getNodeId())
                .tenantId(request.getTenantId())
                .reason(request.getReason())
                .approvers(request.getApprovers())
                .status(request.getStatus() == null ? null : request.getStatus().name())
                .decidedBy(request.getDecidedBy())
                .decisionComment(request.getDecisionComment())
                .createdAt(request.getCreatedAt())
                .decidedAt(request.getDecidedAt())
                .expiresAt(request.getExpiresAt())
                .build();
    }
}
