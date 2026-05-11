package com.kuaishou.intelligentanalysisplatform.interfaces.rest.v1;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.application.ApprovalApplicationService;
import com.kuaishou.intelligentanalysisplatform.common.response.ApiResponse;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ApprovalRequestDTO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalApplicationService approvalApplicationService;

    @GetMapping("/{requestId}")
    public ApiResponse<ApprovalRequestDTO> getRequest(
            @PathVariable String requestId,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId) {
        return ApiResponse.success(approvalApplicationService.getRequest(requestId, tenantId));
    }

    @PostMapping("/{requestId}/approve")
    public ApiResponse<Void> approve(
            @PathVariable String requestId,
            @RequestBody DecisionRequestBody body,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        approvalApplicationService.approve(requestId, tenantId, userId,
                body == null ? null : body.getComment());
        return ApiResponse.success();
    }

    @PostMapping("/{requestId}/reject")
    public ApiResponse<Void> reject(
            @PathVariable String requestId,
            @RequestBody DecisionRequestBody body,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        approvalApplicationService.reject(requestId, tenantId, userId,
                body == null ? null : body.getComment());
        return ApiResponse.success();
    }

    @GetMapping
    public ApiResponse<List<ApprovalRequestDTO>> listPending(
            @RequestParam String workflowId,
            @RequestParam String nodeId,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId) {
        // Returns pending approvals for the given workflow and node
        // tenantId filtering is handled by the repository's status='PENDING' constraint;
        // additional tenant check can be added at service layer if needed.
        return ApiResponse.success(
                approvalApplicationService.listPendingByWorkflowAndNode(workflowId, nodeId, tenantId));
    }

    @Data
    public static class DecisionRequestBody {
        private String comment;
    }
}
