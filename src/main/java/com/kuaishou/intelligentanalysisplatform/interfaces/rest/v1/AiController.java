package com.kuaishou.intelligentanalysisplatform.interfaces.rest.v1;

import java.util.List;
import java.util.UUID;

import com.kuaishou.intelligentanalysisplatform.application.ai.AiChartRecommendationService;
import com.kuaishou.intelligentanalysisplatform.application.ai.AiSqlGenerationService;
import com.kuaishou.intelligentanalysisplatform.application.ai.AiWorkflowBuilderService;
import com.kuaishou.intelligentanalysisplatform.common.response.ApiResponse;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowDefinitionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiChartRecommendRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiSqlRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiWorkflowBuildRequestDTO;
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

/**
 * AI 辅助功能接口：SQL 生成（SSE）、图表推荐、工作流自动构建。
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private static final long SQL_STREAM_TIMEOUT_MS = 60_000L;

    private final AiSqlGenerationService aiSqlGenerationService;
    private final AiChartRecommendationService aiChartRecommendationService;
    private final AiWorkflowBuilderService aiWorkflowBuilderService;

    /**
     * AI SQL 生成（SSE 流式）。
     * 事件：token（每个文字片段）、done（完成）、error（出错）
     */
    @PostMapping(value = "/sql/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateSql(
            @Valid @RequestBody AiSqlRequestDTO request,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        SseEmitter emitter = new SseEmitter(SQL_STREAM_TIMEOUT_MS);
        RequestContextDTO context = contextOf(tenantId, userId);
        aiSqlGenerationService.generateSql(request, context, emitter);
        return emitter;
    }

    /**
     * AI 图表类型推荐（同步）。
     * 先走规则引擎，未命中时调 LLM。
     */
    @PostMapping("/chart/recommend")
    public ApiResponse<List<ChartRecommendationDTO>> recommendChart(
            @Valid @RequestBody AiChartRecommendRequestDTO request) {
        return ApiResponse.success(aiChartRecommendationService.recommend(request));
    }

    /**
     * AI 工作流自动构建（同步）。
     * 返回完整的 WorkflowDefinitionDTO 草稿，前端可直接加载到编辑器。
     */
    @PostMapping("/workflow/build")
    public ApiResponse<WorkflowDefinitionDTO> buildWorkflow(
            @Valid @RequestBody AiWorkflowBuildRequestDTO request,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        RequestContextDTO context = contextOf(tenantId, userId);
        return ApiResponse.success(aiWorkflowBuilderService.buildDraft(request, context));
    }

    private RequestContextDTO contextOf(String tenantId, String userId) {
        return RequestContextDTO.builder()
                .tenantId(tenantId)
                .userId(userId)
                .requestId(UUID.randomUUID().toString())
                .build();
    }
}
