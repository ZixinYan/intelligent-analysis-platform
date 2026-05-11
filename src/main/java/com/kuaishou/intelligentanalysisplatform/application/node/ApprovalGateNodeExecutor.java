package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.Map;
import java.util.UUID;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ApprovalStatus;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.enums.NodeType;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ResultKind;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ApprovalGateNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecutor;
import com.kuaishou.intelligentanalysisplatform.contract.spi.ValidationResultDTO;
import com.kuaishou.intelligentanalysisplatform.domain.approval.ApprovalRequest;
import com.kuaishou.intelligentanalysisplatform.domain.approval.ApprovalRequestRepository;
import org.springframework.stereotype.Component;

@Component
public class ApprovalGateNodeExecutor implements NodeExecutor<ApprovalGateNodeConfigDTO> {

    private final ApprovalRequestRepository approvalRequestRepository;

    public ApprovalGateNodeExecutor(ApprovalRequestRepository approvalRequestRepository) {
        this.approvalRequestRepository = approvalRequestRepository;
    }

    @Override
    public String supportType() {
        return NodeType.APPROVAL_GATE.getCode();
    }

    @Override
    public NodeResultDTO execute(NodeExecuteContextDTO context, ApprovalGateNodeConfigDTO config) {
        String requestId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        Long expiresAt = config.getTimeoutSeconds() != null && config.getTimeoutSeconds() > 0
                ? now + config.getTimeoutSeconds() * 1000L : null;

        ApprovalRequest request = ApprovalRequest.builder()
                .requestId(requestId)
                .workflowId(context.getWorkflowId())
                .nodeId(context.getNodeId())
                .tenantId(context.getRequestContext() != null ? context.getRequestContext().getTenantId() : null)
                .reason(config.getReasonTemplate())
                .approvers(config.getApprovers())
                .status(ApprovalStatus.PENDING)
                .createdAt(now)
                .expiresAt(expiresAt)
                .build();
        approvalRequestRepository.save(request);

        return NodeResultDTO.builder()
                .nodeId(context.getNodeId())
                .nodeType(supportType())
                .status(ExecutionStatus.PENDING)
                .result(StandardResultDTO.builder()
                        .kind(ResultKind.VARIABLES)
                        .variables(Map.of(
                                "approvalRequestId", requestId,
                                "approvalStatus", "PENDING",
                                "expiresAt", expiresAt != null ? expiresAt : "never"))
                        .build())
                .build();
    }

    @Override
    public ValidationResultDTO validate(ApprovalGateNodeConfigDTO config) {
        if (config == null || config.getApprovers() == null || config.getApprovers().isEmpty()) {
            return ValidationResultDTO.builder().valid(false).errorMessage("approvers is required").build();
        }
        return ValidationResultDTO.builder().valid(true).build();
    }

    @Override
    public NodeMetaDTO metadata() {
        return null;
    }
}
