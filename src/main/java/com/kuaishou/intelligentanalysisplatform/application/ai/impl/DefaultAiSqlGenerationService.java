package com.kuaishou.intelligentanalysisplatform.application.ai.impl;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.application.DatasourceApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.ai.AiProviderClient;
import com.kuaishou.intelligentanalysisplatform.application.ai.AiSqlGenerationService;
import com.kuaishou.intelligentanalysisplatform.application.ai.ConversationContextService;
import com.kuaishou.intelligentanalysisplatform.application.knowledge.KnowledgeBaseService;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.KnowledgeChunkDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiSqlRequestDTO;
import com.kuaishou.intelligentanalysisplatform.domain.conversation.Conversation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class DefaultAiSqlGenerationService implements AiSqlGenerationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiSqlGenerationService.class);

    private final AiProviderClient aiProviderClient;
    private final DatasourceApplicationService datasourceService;
    private final ConversationContextService conversationContextService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ObjectMapper objectMapper;

    public DefaultAiSqlGenerationService(AiProviderClient aiProviderClient,
                                         DatasourceApplicationService datasourceService,
                                         ConversationContextService conversationContextService,
                                         KnowledgeBaseService knowledgeBaseService,
                                         ObjectMapper objectMapper) {
        this.aiProviderClient = aiProviderClient;
        this.datasourceService = datasourceService;
        this.conversationContextService = conversationContextService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.objectMapper = objectMapper;
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

            // 2.5 可选：RAG 知识库增强
            if (request.getKnowledgeBaseId() != null && !request.getKnowledgeBaseId().isBlank()) {
                try {
                    List<KnowledgeChunkDTO> ragChunks = knowledgeBaseService.retrieve(
                            request.getKnowledgeBaseId(), request.getDescription(), 5);
                    if (!ragChunks.isEmpty()) {
                        String ragContext = ragChunks.stream()
                                .map(c -> c.getDocTitle() + ": " + c.getContent())
                                .collect(Collectors.joining("\n"));
                        systemPrompt = systemPrompt
                                + "\n\nAdditional business context (use only if relevant):\n" + ragContext;
                    }
                } catch (Exception e) {
                    log.warn("RAG retrieval failed, continuing without context: {}", e.getMessage());
                }
            }

            // 3. 获取或创建会话
            Conversation conv = conversationContextService.getOrCreate(
                    request.getConversationId(), context.getTenantId(), context.getUserId());

            // 4. 准备带历史的 messages 并持久化本轮用户消息
            List<Map<String, String>> history = conversationContextService
                    .prepareAndSave(conv, systemPrompt, request.getDescription());

            // 5. 累积 Assistant 回复
            StringBuilder assistantBuffer = new StringBuilder();

            // 6. 带历史的流式调用
            aiProviderClient.streamCompletionWithHistory(
                    history,
                    request.getDescription(),
                    token -> {
                        assistantBuffer.append(token);
                        sendToken(emitter, token);
                    },
                    () -> {
                        conversationContextService.appendAssistantReply(
                                conv.getConversationId(), assistantBuffer.toString());
                        sendDoneWithConvId(emitter, conv.getConversationId());
                    },
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

    private void sendDoneWithConvId(SseEmitter emitter, String conversationId) {
        try {
            String data = objectMapper.writeValueAsString(Map.of("conversationId", conversationId));
            emitter.send(SseEmitter.event().name("done").data(data));
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
