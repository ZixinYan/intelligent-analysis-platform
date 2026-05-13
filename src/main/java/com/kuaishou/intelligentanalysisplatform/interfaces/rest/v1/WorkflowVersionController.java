package com.kuaishou.intelligentanalysisplatform.interfaces.rest.v1;

import com.kuaishou.intelligentanalysisplatform.application.WorkflowVersionApplicationService;
import com.kuaishou.intelligentanalysisplatform.common.response.ApiResponse;
import com.kuaishou.intelligentanalysisplatform.common.response.PageResult;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowDefinitionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowVersionDiffDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowVersionDTO;
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
@RequestMapping("/api/v1/workflows/{workflowId}/versions")
@RequiredArgsConstructor
public class WorkflowVersionController {

    private final WorkflowVersionApplicationService workflowVersionApplicationService;

    /** POST /api/v1/workflows/{workflowId}/versions — 手动创建快照 */
    @PostMapping
    public ApiResponse<WorkflowVersionDTO> snapshot(@PathVariable String workflowId,
                                                     @RequestBody(required = false) SnapshotRequest body,
                                                     @RequestHeader("X-Tenant-Id") String tenantId,
                                                     @RequestHeader("X-User-Id") String userId) {
        String summary = body != null ? body.changeSummary() : null;
        return ApiResponse.success(workflowVersionApplicationService.snapshot(workflowId, summary, contextOf(tenantId, userId)));
    }

    /** GET /api/v1/workflows/{workflowId}/versions — 版本列表 */
    @GetMapping
    public ApiResponse<PageResult<WorkflowVersionDTO>> list(@PathVariable String workflowId,
                                                             @RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "20") int pageSize,
                                                             @RequestHeader("X-Tenant-Id") String tenantId,
                                                             @RequestHeader("X-User-Id") String userId) {
        return ApiResponse.success(workflowVersionApplicationService.listVersions(
                workflowId, page, pageSize, contextOf(tenantId, userId)));
    }

    /** GET /api/v1/workflows/{workflowId}/versions/{versionNumber} — 指定版本完整定义 */
    @GetMapping("/{versionNumber}")
    public ApiResponse<WorkflowDefinitionDTO> getVersion(@PathVariable String workflowId,
                                                          @PathVariable int versionNumber,
                                                          @RequestHeader("X-Tenant-Id") String tenantId,
                                                          @RequestHeader("X-User-Id") String userId) {
        return ApiResponse.success(workflowVersionApplicationService.getVersion(
                workflowId, versionNumber, contextOf(tenantId, userId)));
    }

    /** POST /api/v1/workflows/{workflowId}/versions/{versionNumber}/publish — 发布版本 */
    @PostMapping("/{versionNumber}/publish")
    public ApiResponse<Void> publish(@PathVariable String workflowId,
                                      @PathVariable int versionNumber,
                                      @RequestHeader("X-Tenant-Id") String tenantId,
                                      @RequestHeader("X-User-Id") String userId) {
        workflowVersionApplicationService.publish(workflowId, versionNumber, contextOf(tenantId, userId));
        return ApiResponse.success();
    }

    /** POST /api/v1/workflows/{workflowId}/versions/{versionNumber}/rollback — 回滚为当前草稿 */
    @PostMapping("/{versionNumber}/rollback")
    public ApiResponse<WorkflowVersionDTO> rollback(@PathVariable String workflowId,
                                                     @PathVariable int versionNumber,
                                                     @RequestHeader("X-Tenant-Id") String tenantId,
                                                     @RequestHeader("X-User-Id") String userId) {
        return ApiResponse.success(workflowVersionApplicationService.rollback(
                workflowId, versionNumber, contextOf(tenantId, userId)));
    }

    /** GET /api/v1/workflows/{workflowId}/versions/diff?from={v1}&to={v2} — 版本差异 */
    @GetMapping("/diff")
    public ApiResponse<WorkflowVersionDiffDTO> diff(@PathVariable String workflowId,
                                                     @RequestParam int from,
                                                     @RequestParam int to,
                                                     @RequestHeader("X-Tenant-Id") String tenantId,
                                                     @RequestHeader("X-User-Id") String userId) {
        return ApiResponse.success(workflowVersionApplicationService.diff(
                workflowId, from, to, contextOf(tenantId, userId)));
    }

    private RequestContextDTO contextOf(String tenantId, String userId) {
        return RequestContextDTO.builder()
                .tenantId(tenantId)
                .userId(userId)
                .requestId(null)
                .build();
    }

    record SnapshotRequest(String changeSummary) {}
}
