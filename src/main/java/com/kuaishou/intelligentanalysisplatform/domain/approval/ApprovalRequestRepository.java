package com.kuaishou.intelligentanalysisplatform.domain.approval;

import java.util.List;
import java.util.Optional;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ApprovalStatus;

public interface ApprovalRequestRepository {
    void save(ApprovalRequest request);
    void updateDecision(String requestId, ApprovalStatus status,
                        String decidedBy, String comment, Long decidedAt);
    Optional<ApprovalRequest> findById(String requestId);
    List<ApprovalRequest> findPendingByWorkflowAndNode(String workflowId, String nodeId);
}
