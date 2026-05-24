package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.application.ai.rag.AiRagContextFormatter;
import com.kuaishou.intelligentanalysisplatform.application.knowledge.KnowledgeBaseService;
import com.kuaishou.intelligentanalysisplatform.contract.schema.KnowledgeChunkDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.KnowledgeRetrievalNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KnowledgeRetrievalNodeExecutorTest {

    @Test
    void shouldUseQueryLiteralAndFormatOutput() {
        KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
        KnowledgeRetrievalNodeExecutor executor = new KnowledgeRetrievalNodeExecutor(knowledgeBaseService, new AiRagContextFormatter());
        when(knowledgeBaseService.retrieve(eq("kb-1"), eq("最近7天订单数"), eq(5))).thenReturn(List.of(
                KnowledgeChunkDTO.builder().docTitle("规则").content("只统计支付成功订单").build()
        ));

        var result = executor.execute(context(), KnowledgeRetrievalNodeConfigDTO.builder()
                .knowledgeBaseId("kb-1")
                .queryLiteral("最近7天订单数")
                .build());

        assertEquals("【规则】\n只统计支付成功订单", result.getResult().getVariables().get("retrieved_context"));
        verify(knowledgeBaseService).retrieve("kb-1", "最近7天订单数", 5);
    }

    @Test
    void shouldResolveQueryFromUpstreamVariable() {
        KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
        KnowledgeRetrievalNodeExecutor executor = new KnowledgeRetrievalNodeExecutor(knowledgeBaseService, new AiRagContextFormatter());
        when(knowledgeBaseService.retrieve(eq("kb-1"), eq("最近7天订单数"), eq(3))).thenReturn(List.of());

        executor.execute(contextWithUpstream(), KnowledgeRetrievalNodeConfigDTO.builder()
                .knowledgeBaseId("kb-1")
                .queryVariable("$.question")
                .topK(3)
                .outputVariable("rag_context")
                .build());

        verify(knowledgeBaseService).retrieve("kb-1", "最近7天订单数", 3);
    }

    @Test
    void shouldSkipRetrieveWhenResolvedQueryBlank() {
        KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
        KnowledgeRetrievalNodeExecutor executor = new KnowledgeRetrievalNodeExecutor(knowledgeBaseService, new AiRagContextFormatter());

        var result = executor.execute(context(), KnowledgeRetrievalNodeConfigDTO.builder()
                .knowledgeBaseId("kb-1")
                .queryVariable("$.missing")
                .build());

        assertEquals("", result.getResult().getVariables().get("retrieved_context"));
        verifyNoInteractions(knowledgeBaseService);
    }

    @Test
    void shouldValidateConfig() {
        KnowledgeRetrievalNodeExecutor executor = new KnowledgeRetrievalNodeExecutor(mock(KnowledgeBaseService.class), new AiRagContextFormatter());

        assertTrue(executor.validate(KnowledgeRetrievalNodeConfigDTO.builder()
                .knowledgeBaseId("kb-1")
                .queryLiteral("query")
                .build()).isValid());
        assertFalse(executor.validate(KnowledgeRetrievalNodeConfigDTO.builder()
                .queryLiteral("query")
                .build()).isValid());
    }

    private NodeExecuteContextDTO context() {
        return NodeExecuteContextDTO.builder()
                .nodeId("node-1")
                .requestContext(RequestContextDTO.builder().tenantId("t1").userId("u1").build())
                .build();
    }

    private NodeExecuteContextDTO contextWithUpstream() {
        return NodeExecuteContextDTO.builder()
                .nodeId("node-1")
                .requestContext(RequestContextDTO.builder().tenantId("t1").userId("u1").build())
                .upstreamResults(Map.of("upstream", StandardResultDTO.builder()
                        .variables(Map.of("question", "最近7天订单数"))
                        .build()))
                .build();
    }
}
