package com.kuaishou.intelligentanalysisplatform.domain.approval;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ApprovalStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApprovalRequest {
    private String requestId;
    private String workflowId;
    private String nodeId;
    private String tenantId;
    private String reason;
    private List<String> approvers;   // JSON 序列化存储
    private ApprovalStatus status;
    private String decidedBy;
    private String decisionComment;
    private Long createdAt;
    private Long decidedAt;
    private Long expiresAt;           // null = 永不超时

    public boolean isPending() {
        return status == ApprovalStatus.PENDING;
    }

    public boolean isExpired() {
        return expiresAt != null && System.currentTimeMillis() > expiresAt;
    }
}
