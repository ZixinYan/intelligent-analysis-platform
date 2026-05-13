package com.kuaishou.intelligentanalysisplatform.interfaces.rest.v1;

import java.util.UUID;

import com.kuaishou.intelligentanalysisplatform.application.impl.WorkflowStreamExecutor;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeDebugRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowRunRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 流式执行端点（SSE）。
 * 前端通过 fetch + ReadableStream 消费（POST 带 JSON body，不能用 EventSource）。
 */
@RestController
@RequestMapping("/api/v1/workflow-stream")
@RequiredArgsConstructor
public class WorkflowStreamController {

    private static final long WORKFLOW_STREAM_TIMEOUT_MS = 10 * 60 * 1000L;
    private static final long NODE_DEBUG_STREAM_TIMEOUT_MS = 5 * 60 * 1000L;

    private final WorkflowStreamExecutor workflowStreamExecutor;

    /**
     * 多节点 DAG 工作流流式执行。
     * 事件序列：node_start → (node_progress)* → node_result → … → workflow_done | workflow_error
     */
    @PostMapping(value = "/{workflowId}/run",
                 produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter runStream(
            @PathVariable String workflowId,
            @RequestBody WorkflowRunRequestDTO request,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        if (request.getContext() == null) {
            request.setContext(RequestContextDTO.builder()
                    .tenantId(tenantId)
                    .userId(userId)
                    .requestId(UUID.randomUUID().toString())
                    .build());
        }
        request.setWorkflowId(workflowId);

        SseEmitter emitter = new SseEmitter(WORKFLOW_STREAM_TIMEOUT_MS);
        workflowStreamExecutor.executeWorkflow(request, emitter);
        return emitter;
    }

    /**
     * 单节点调试流式执行（大 SQL 结果分块推送）。
     */
    @PostMapping(value = "/node/debug",
                 produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter debugStream(
            @RequestBody NodeDebugRequestDTO request,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        if (request.getContext() == null) {
            request.setContext(RequestContextDTO.builder()
                    .tenantId(tenantId)
                    .userId(userId)
                    .requestId(UUID.randomUUID().toString())
                    .build());
        }

        SseEmitter emitter = new SseEmitter(NODE_DEBUG_STREAM_TIMEOUT_MS);
        workflowStreamExecutor.executeNode(request, emitter);
        return emitter;
    }
}
