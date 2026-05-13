package com.kuaishou.intelligentanalysisplatform.interfaces.rest.v1;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.common.response.ApiResponse;
import com.kuaishou.intelligentanalysisplatform.common.response.PageResult;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowRunLogDTO;
import com.kuaishou.intelligentanalysisplatform.domain.workflow.WorkflowRunLog;
import com.kuaishou.intelligentanalysisplatform.domain.workflow.WorkflowRunLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflows/{workflowId}/runs")
@RequiredArgsConstructor
public class WorkflowRunLogController {

    private static final Logger log = LoggerFactory.getLogger(WorkflowRunLogController.class);

    private final WorkflowRunLogRepository workflowRunLogRepository;
    private final ObjectMapper             objectMapper;

    /**
     * GET /api/v1/workflows/{workflowId}/runs?page=1&pageSize=20&status=SUCCEEDED
     */
    @GetMapping
    public ApiResponse<PageResult<WorkflowRunLogDTO>> listRuns(
            @PathVariable String workflowId,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false)    String status,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Id")   String userId) {

        int offset = (page - 1) * pageSize;
        List<WorkflowRunLog> items = workflowRunLogRepository.findByWorkflowId(workflowId, status, offset, pageSize);
        long total = workflowRunLogRepository.countByWorkflowId(workflowId, status);

        List<WorkflowRunLogDTO> dtos = items.stream()
                .map(r -> toDTO(r, false))
                .toList();

        return ApiResponse.success(PageResult.<WorkflowRunLogDTO>builder()
                .items(dtos)
                .total(total)
                .page(page)
                .pageSize(pageSize)
                .build());
    }

    /**
     * GET /api/v1/workflows/{workflowId}/runs/{runId}
     */
    @GetMapping("/{runId}")
    public ApiResponse<WorkflowRunLogDTO> getRunDetail(
            @PathVariable String workflowId,
            @PathVariable String runId,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Id")   String userId) {

        WorkflowRunLog runLog = workflowRunLogRepository.findByRunId(runId)
                .orElseThrow(() -> new BaseBusinessException(ErrorCode.EXECUTION_RECORD_NOT_FOUND,
                        "run not found: " + runId));
        return ApiResponse.success(toDTO(runLog, true));
    }

    // ─── 转换 ─────────────────────────────────────────────────────────────────

    private WorkflowRunLogDTO toDTO(WorkflowRunLog r, boolean includeTraces) {
        List<WorkflowRunLogDTO.NodeTraceDTO> traces = Collections.emptyList();
        if (includeTraces && r.getNodeTraceJson() != null && !r.getNodeTraceJson().isBlank()) {
            try {
                traces = objectMapper.readValue(r.getNodeTraceJson(),
                        new TypeReference<List<WorkflowRunLogDTO.NodeTraceDTO>>() {});
            } catch (Exception e) {
                log.warn("Failed to parse node_trace_json for run {}: {}", r.getRunId(), e.getMessage());
            }
        }
        return WorkflowRunLogDTO.builder()
                .runId(r.getRunId())
                .workflowId(r.getWorkflowId())
                .versionId(r.getVersionId())
                .tenantId(r.getTenantId())
                .status(r.getStatus())
                .triggerType(r.getTriggerType())
                .nodeCount(r.getNodeCount())
                .startedAt(r.getStartedAt())
                .finishedAt(r.getFinishedAt())
                .elapsedMs(r.getElapsedMs())
                .createdBy(r.getCreatedBy())
                .nodeTraces(traces)
                .build();
    }
}
