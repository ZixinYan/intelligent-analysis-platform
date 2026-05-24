package com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.sql;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.kuaishou.intelligentanalysisplatform.application.ai.foundation.conversation.AiConversationService;
import com.kuaishou.intelligentanalysisplatform.application.ai.foundation.prompt.PromptTemplateService;
import com.kuaishou.intelligentanalysisplatform.application.ai.foundation.provider.AiModelProvider;
import com.kuaishou.intelligentanalysisplatform.application.ai.rag.AiRagOrchestrationService;
import com.kuaishou.intelligentanalysisplatform.application.ai.rag.AiRagQuery;
import com.kuaishou.intelligentanalysisplatform.application.ai.rag.AiRagResult;
import com.kuaishou.intelligentanalysisplatform.application.ai.rag.AiRagScene;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiSqlRequestDTO;
import com.kuaishou.intelligentanalysisplatform.domain.conversation.Conversation;
import org.springframework.stereotype.Component;

@Component
public class AiSqlPromptContextAssembler {

    private static final int DEFAULT_RAG_TOP_K = 5;

    private final SchemaIntrospectionPort schemaIntrospectionPort;
    private final AiRagOrchestrationService aiRagOrchestrationService;
    private final AiConversationService aiConversationService;
    private final PromptTemplateService promptTemplateService;

    public AiSqlPromptContextAssembler(SchemaIntrospectionPort schemaIntrospectionPort,
                                       AiRagOrchestrationService aiRagOrchestrationService,
                                       AiConversationService aiConversationService,
                                       PromptTemplateService promptTemplateService) {
        this.schemaIntrospectionPort = schemaIntrospectionPort;
        this.aiRagOrchestrationService = aiRagOrchestrationService;
        this.aiConversationService = aiConversationService;
        this.promptTemplateService = promptTemplateService;
    }

    public AiSqlPromptContext assemble(AiSqlRequestDTO request, RequestContextDTO context) {
        List<FieldSchemaDTO> fields = schemaIntrospectionPort.introspectTableSchema(
                request.getDatasourceId(), request.getTableName(), context);
        String systemPrompt = promptTemplateService.load("sql-generation.txt", Map.of(
                "DB_TYPE", "SQL",
                "TABLE_NAME", request.getTableName(),
                "COLUMNS", buildColumnsDesc(fields)
        ));
        String enrichedPrompt = appendKnowledgeContext(systemPrompt, request);
        Conversation conversation = aiConversationService.getOrCreate(
                request.getConversationId(), context.getTenantId(), context.getUserId());
        List<AiModelProvider.AiMessage> history = aiConversationService
                .prepareAndSave(conversation, enrichedPrompt, request.getDescription());
        return new AiSqlPromptContext(conversation, enrichedPrompt, request.getDescription(), history);
    }

    private String buildColumnsDesc(List<FieldSchemaDTO> fields) {
        return fields.stream()
                .map(f -> "  - " + f.getName()
                        + " (" + (f.getValueType() != null ? f.getValueType().name() : "UNKNOWN") + ")"
                        + (f.getSemanticType() != null ? " [" + f.getSemanticType().name() + "]" : ""))
                .collect(Collectors.joining("\n"));
    }

    private String appendKnowledgeContext(String systemPrompt, AiSqlRequestDTO request) {
        AiRagResult ragResult = aiRagOrchestrationService.retrieve(AiRagQuery.builder()
                .knowledgeBaseId(request.getKnowledgeBaseId())
                .queryText(request.getDescription())
                .topK(DEFAULT_RAG_TOP_K)
                .scene(AiRagScene.SQL_PROMPT)
                .build());
        if (!ragResult.hasResults()) {
            return systemPrompt;
        }
        return systemPrompt + "\n\n" + ragResult.getFormattedContext();
    }
}
