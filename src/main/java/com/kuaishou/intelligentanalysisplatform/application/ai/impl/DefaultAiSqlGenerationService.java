package com.kuaishou.intelligentanalysisplatform.application.ai.impl;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.kuaishou.intelligentanalysisplatform.application.DatasourceApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.ai.AiProviderClient;
import com.kuaishou.intelligentanalysisplatform.application.ai.AiSqlGenerationService;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiSqlRequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class DefaultAiSqlGenerationService implements AiSqlGenerationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiSqlGenerationService.class);
    private static final long SSE_TIMEOUT_MS = 60_000L;

    private final AiProviderClient aiProviderClient;
    private final DatasourceApplicationService datasourceService;

    public DefaultAiSqlGenerationService(AiProviderClient aiProviderClient,
                                         DatasourceApplicationService datasourceService) {
        this.aiProviderClient = aiProviderClient;
        this.datasourceService = datasourceService;
    }

    @Override
    public void generateSql(AiSqlRequestDTO request, RequestContextDTO context, SseEmitter emitter) {
        try {
            // 1. 获取表字段 Schema
            List<FieldSchemaDTO> fields = datasourceService.introspectTableSchema(
                    request.getDatasourceId(), request.getTableName(), context);

            // 2. 构建 Prompt
            String columnsDesc = fields.stream()
                    .map(f -> "  - " + f.getName()
                            + " (" + (f.getValueType() != null ? f.getValueType().name() : "UNKNOWN") + ")"
                            + (f.getSemanticType() != null ? " [" + f.getSemanticType().name() + "]" : ""))
                    .collect(Collectors.joining("\n"));

            String systemPrompt = PromptLoader.load("sql-generation.txt", Map.of(
                    "DB_TYPE", "SQL",
                    "TABLE_NAME", request.getTableName(),
                    "COLUMNS", columnsDesc
            ));

            // 3. 流式调用 LLM，通过 SseEmitter 推送
            aiProviderClient.streamCompletion(
                    systemPrompt,
                    request.getDescription(),
                    token -> sendToken(emitter, token),
                    () -> completeEmitter(emitter),
                    error -> {
                        log.error("AI SQL generation failed", error);
                        sendError(emitter, error.getMessage());
                    }
            );
        } catch (Exception e) {
            log.error("Failed to start AI SQL generation", e);
            sendError(emitter, e.getMessage());
        }
    }

    private void sendToken(SseEmitter emitter, String token) {
        try {
            emitter.send(SseEmitter.event().name("token").data(token));
        } catch (IOException e) {
            log.debug("SSE send token failed (client disconnected)");
        }
    }

    private void completeEmitter(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("done").data(""));
            emitter.complete();
        } catch (IOException e) {
            log.debug("SSE complete failed");
        }
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(message != null ? message : "AI generation failed"));
            emitter.complete();
        } catch (IOException e) {
            log.debug("SSE error send failed");
        }
    }
}
