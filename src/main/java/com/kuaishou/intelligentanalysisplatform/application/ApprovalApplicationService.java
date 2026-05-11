package com.kuaishou.intelligentanalysisplatform.application;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.contract.schema.ApprovalRequestDTO;

public interface ApprovalApplicationService {
    ApprovalRequestDTO getRequest(String requestId, String tenantId);
    void approve(String requestId, String tenantId, String decidedBy, String comment);
    void reject(String requestId, String tenantId, String decidedBy, String comment);
    List<ApprovalRequestDTO> listPendingByWorkflowAndNode(String workflowId, String nodeId, String tenantId);
}
