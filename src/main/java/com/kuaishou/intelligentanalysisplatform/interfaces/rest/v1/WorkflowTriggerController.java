package com.kuaishou.intelligentanalysisplatform.interfaces.rest.v1;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.application.trigger.TriggerApplicationService;
import com.kuaishou.intelligentanalysisplatform.common.response.ApiResponse;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AsyncSubmitResponseDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.CreateTriggerRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.TriggerDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.UpdateTriggerStatusRequestDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WorkflowTriggerController {

    private final TriggerApplicationService triggerApplicationService;

    /** 创建触发器（SCHEDULE 或 WEBHOOK） */
    @PostMapping("/workflows/{id}/triggers")
    public ApiResponse<TriggerDTO> create(
            @PathVariable("id") String workflowId,
            @Valid @RequestBody CreateTriggerRequestDTO request,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        return ApiResponse.success(triggerApplicationService.createTrigger(workflowId, tenantId, request));
    }

    /** 列出触发器 */
    @GetMapping("/workflows/{id}/triggers")
    public ApiResponse<List<TriggerDTO>> list(
            @PathVariable("id") String workflowId,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        return ApiResponse.success(triggerApplicationService.listTriggers(workflowId, tenantId));
    }

    /** 暂停/恢复触发器 */
    @PatchMapping("/triggers/{triggerId}/status")
    public ApiResponse<TriggerDTO> updateStatus(
            @PathVariable String triggerId,
            @Valid @RequestBody UpdateTriggerStatusRequestDTO request,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        return ApiResponse.success(triggerApplicationService.updateStatus(triggerId, request.getStatus(), tenantId));
    }

    /** 删除触发器 */
    @DeleteMapping("/triggers/{triggerId}")
    public ApiResponse<Void> delete(
            @PathVariable String triggerId,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        triggerApplicationService.deleteTrigger(triggerId, tenantId);
        return ApiResponse.success();
    }

    /** 手动立即触发一次（测试用） */
    @PostMapping("/triggers/{triggerId}/fire")
    public ApiResponse<AsyncSubmitResponseDTO> fire(
            @PathVariable String triggerId,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        return ApiResponse.success(triggerApplicationService.fireTrigger(triggerId, tenantId));
    }
}
