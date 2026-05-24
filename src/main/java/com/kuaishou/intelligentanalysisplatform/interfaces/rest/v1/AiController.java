package com.kuaishou.intelligentanalysisplatform.interfaces.rest.v1;

import java.util.List;
import java.util.UUID;

import com.kuaishou.intelligentanalysisplatform.application.ai.agent.AiAgentService;
import com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.AiChartOrchestrationService;
import com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.AiSqlOrchestrationService;
import com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.AiStreamOutputHandler;
import com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.AiWorkflowOrchestrationService;
import com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.sql.AiSseStreamResponder;
import com.kuaishou.intelligentanalysisplatform.common.response.ApiResponse;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowDefinitionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiChartRecommendRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiChatRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiSqlRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowBuildRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowBuildResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowDryRunRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowDryRunResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowExecuteRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowExecuteResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowLoadResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowSaveRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowSaveResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.ChartRecommendationDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private static final long SQL_STREAM_TIMEOUT_MS = 60_000L;
    private static final long CHAT_STREAM_TIMEOUT_MS = 60_000L;
    private static final String RESPONSE_MODE_ENVELOPE = "ENVELOPE";

    private final AiSqlOrchestrationService aiSqlOrchestrationService;
    private final AiChartOrchestrationService aiChartOrchestrationService;
    private final AiWorkflowOrchestrationService aiWorkflowOrchestrationService;
    private final AiAgentService aiAgentService;
    private final AiSseStreamResponder sseStreamResponder;

    @PostMapping(value = "/sql/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateSql(
            @Valid @RequestBody AiSqlRequestDTO request,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        SseEmitter emitter = new SseEmitter(SQL_STREAM_TIMEOUT_MS);
        RequestContextDTO context = contextOf(tenantId, userId);
        aiSqlOrchestrationService.generateSql(request, context, sseHandlerFor(emitter));
        return emitter;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody AiChatRequestDTO request,
                           @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
                           @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        SseEmitter emitter = new SseEmitter(CHAT_STREAM_TIMEOUT_MS);
        aiAgentService.streamChat(request, contextOf(tenantId, userId), sseHandlerFor(emitter));
        return emitter;
    }

    @PostMapping("/chart/recommend")
    public ApiResponse<List<ChartRecommendationDTO>> recommendChart(
            @Valid @RequestBody AiChartRecommendRequestDTO request) {
        return ApiResponse.success(aiChartOrchestrationService.recommend(request));
    }

    @PostMapping("/workflow/build")
    public ApiResponse<?> buildWorkflow(
            @Valid @RequestBody AiWorkflowBuildRequestDTO request,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        RequestContextDTO context = contextOf(tenantId, userId);
        if (RESPONSE_MODE_ENVELOPE.equalsIgnoreCase(request.getResponseMode())) {
            AiWorkflowBuildResultDTO result = aiWorkflowOrchestrationService.build(request, context);
            return ApiResponse.success(result);
        }
        WorkflowDefinitionDTO draft = aiWorkflowOrchestrationService.buildDraft(request, context);
        return ApiResponse.success(draft);
    }

    @PostMapping("/workflow/save")
    public ApiResponse<AiWorkflowSaveResultDTO> saveWorkflow(
            @Valid @RequestBody AiWorkflowSaveRequestDTO request,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        return ApiResponse.success(aiWorkflowOrchestrationService.saveDraft(request, contextOf(tenantId, userId)));
    }

    @PostMapping("/workflow/load")
    public ApiResponse<AiWorkflowLoadResultDTO> loadWorkflow(
            @Valid @RequestBody AiWorkflowDryRunRequestDTO request,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        return ApiResponse.success(aiWorkflowOrchestrationService.loadWorkflow(request.getWorkflowId(), contextOf(tenantId, userId)));
    }

    @PostMapping("/workflow/execute")
    public ApiResponse<AiWorkflowExecuteResultDTO> executeWorkflow(
            @Valid @RequestBody AiWorkflowExecuteRequestDTO request,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        return ApiResponse.success(aiWorkflowOrchestrationService.executeWorkflow(request, contextOf(tenantId, userId)));
    }

    @PostMapping("/workflow/dry-run")
    public ApiResponse<AiWorkflowDryRunResultDTO> dryRunWorkflow(
            @Valid @RequestBody AiWorkflowDryRunRequestDTO request,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        return ApiResponse.success(aiWorkflowOrchestrationService.dryRunWorkflow(request, contextOf(tenantId, userId)));
    }

    /** 将 SseEmitter 桥接为 AiStreamOutputHandler，SSE 相关逻辑收归 Controller 层。 */
    private AiStreamOutputHandler sseHandlerFor(SseEmitter emitter) {
        return new AiStreamOutputHandler() {
            @Override
            public void onToken(String token) {
                sseStreamResponder.sendToken(emitter, token);
            }

            @Override
            public void onDone(String conversationId) {
                sseStreamResponder.sendDoneWithConvId(emitter, conversationId);
            }

            @Override
            public void onError(String message) {
                sseStreamResponder.sendError(emitter, message);
            }
        };
    }

    private RequestContextDTO contextOf(String tenantId, String userId) {
        return RequestContextDTO.builder()
                .tenantId(tenantId)
                .userId(userId)
                .requestId(UUID.randomUUID().toString())
                .build();
    }
}
