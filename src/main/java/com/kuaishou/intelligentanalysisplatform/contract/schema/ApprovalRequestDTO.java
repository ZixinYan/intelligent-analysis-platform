package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApprovalRequestDTO {
    private String requestId;
    private String workflowId;
    private String nodeId;
    private String tenantId;
    private String reason;
    private List<String> approvers;
    private String status;          // PENDING / APPROVED / REJECTED / TIMED_OUT
    private String decidedBy;
    private String decisionComment;
    private Long createdAt;
    private Long decidedAt;
    private Long expiresAt;
}
