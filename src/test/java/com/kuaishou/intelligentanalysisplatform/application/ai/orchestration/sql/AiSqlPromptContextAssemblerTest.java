package com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.sql;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.application.DatasourceApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.ai.foundation.conversation.AiConversationService;
import com.kuaishou.intelligentanalysisplatform.application.ai.foundation.prompt.PromptTemplateService;
import com.kuaishou.intelligentanalysisplatform.application.ai.rag.AiRagOrchestrationService;
import com.kuaishou.intelligentanalysisplatform.application.ai.rag.AiRagResult;
import com.kuaishou.intelligentanalysisplatform.contract.enums.FieldSemanticType;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ValueType;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiSqlRequestDTO;
import com.kuaishou.intelligentanalysisplatform.domain.conversation.Conversation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiSqlPromptContextAssemblerTest {

    @Test
    void shouldAssemblePromptContextWithKnowledge() {
        DatasourceApplicationService datasourceApplicationService = mock(DatasourceApplicationService.class);
        AiRagOrchestrationService ragOrchestrationService = mock(AiRagOrchestrationService.class);
        AiConversationService conversationService = mock(AiConversationService.class);
        PromptTemplateService promptTemplateService = mock(PromptTemplateService.class);
        AiSqlPromptContextAssembler assembler = new AiSqlPromptContextAssembler(
                datasourceApplicationService, ragOrchestrationService, conversationService, promptTemplateService);
        AiSqlRequestDTO request = new AiSqlRequestDTO();
        request.setDatasourceId("ds-1");
        request.setTableName("orders");
        request.setDescription("最近7天订单数");
        request.setKnowledgeBaseId("kb-1");
        RequestContextDTO context = RequestContextDTO.builder().tenantId("tenant-a").userId("user-a").build();
        Conversation conversation = Conversation.builder().conversationId("conv-1").tenantId("tenant-a").userId("user-a").build();

        when(datasourceApplicationService.introspectTableSchema(eq("ds-1"), eq("orders"), eq(context))).thenReturn(List.of(
                FieldSchemaDTO.builder().name("order_id").valueType(ValueType.STRING).semanticType(FieldSemanticType.DIMENSION).build()));
        when(promptTemplateService.load(eq("sql-generation.txt"), any())).thenReturn("base prompt");
        when(ragOrchestrationService.retrieve(any())).thenReturn(AiRagResult.builder()
                .chunks(List.of(com.kuaishou.intelligentanalysisplatform.contract.schema.KnowledgeChunkDTO.builder()
                        .docTitle("规则")
                        .content("只统计支付成功订单")
                        .build()))
                .formattedContext("Additional business context (use only if relevant):\n规则: 只统计支付成功订单")
                .degraded(false)
                .build());
        when(conversationService.getOrCreate(null, "tenant-a", "user-a")).thenReturn(conversation);
        when(conversationService.prepareAndSave(conversation,
                "base prompt\n\nAdditional business context (use only if relevant):\n规则: 只统计支付成功订单",
                "最近7天订单数")).thenReturn(List.of(
                java.util.Map.of("role", "system", "content", "system prompt"),
                java.util.Map.of("role", "user", "content", "最近7天订单数")));

        AiSqlPromptContext promptContext = assembler.assemble(request, context);

        assertEquals("conv-1", promptContext.conversation().getConversationId());
        assertTrue(promptContext.systemPrompt().contains("Additional business context"));
        assertEquals(2, promptContext.history().size());
        verify(datasourceApplicationService).introspectTableSchema("ds-1", "orders", context);
        verify(ragOrchestrationService).retrieve(any());
    }

    @Test
    void shouldFallbackWhenKnowledgeLookupFails() {
        DatasourceApplicationService datasourceApplicationService = mock(DatasourceApplicationService.class);
        AiRagOrchestrationService ragOrchestrationService = mock(AiRagOrchestrationService.class);
        AiConversationService conversationService = mock(AiConversationService.class);
        PromptTemplateService promptTemplateService = mock(PromptTemplateService.class);
        AiSqlPromptContextAssembler assembler = new AiSqlPromptContextAssembler(
                datasourceApplicationService, ragOrchestrationService, conversationService, promptTemplateService);
        AiSqlRequestDTO request = new AiSqlRequestDTO();
        request.setDatasourceId("ds-1");
        request.setTableName("orders");
        request.setDescription("最近7天订单数");
        request.setKnowledgeBaseId("kb-1");
        RequestContextDTO context = RequestContextDTO.builder().tenantId("tenant-a").userId("user-a").build();
        Conversation conversation = Conversation.builder().conversationId("conv-1").tenantId("tenant-a").userId("user-a").build();

        when(datasourceApplicationService.introspectTableSchema(eq("ds-1"), eq("orders"), eq(context))).thenReturn(List.of(
                FieldSchemaDTO.builder().name("order_id").build()));
        when(promptTemplateService.load(eq("sql-generation.txt"), any())).thenReturn("base prompt");
        when(ragOrchestrationService.retrieve(any())).thenReturn(AiRagResult.degraded());
        when(conversationService.getOrCreate(null, "tenant-a", "user-a")).thenReturn(conversation);
        when(conversationService.prepareAndSave(conversation, "base prompt", "最近7天订单数")).thenReturn(List.of());

        AiSqlPromptContext promptContext = assembler.assemble(request, context);

        assertEquals("base prompt", promptContext.systemPrompt());
        verify(conversationService).prepareAndSave(conversation, "base prompt", "最近7天订单数");
    }
}
