package com.kuaishou.intelligentanalysisplatform.application.ai.rag;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.contract.schema.KnowledgeChunkDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiRagOrchestrationServiceTest {

    @Test
    void shouldReturnEmptyWhenKnowledgeBaseMissing() {
        AiRagRetrievalAdapter retrievalAdapter = mock(AiRagRetrievalAdapter.class);
        AiRagOrchestrationService service = new AiRagOrchestrationService(retrievalAdapter, new AiRagContextFormatter());

        AiRagResult result = service.retrieve(AiRagQuery.builder()
                .queryText("最近7天订单数")
                .scene(AiRagScene.SQL_PROMPT)
                .build());

        assertFalse(result.hasResults());
        assertFalse(result.isDegraded());
        verifyNoInteractions(retrievalAdapter);
    }

    @Test
    void shouldNormalizeTopKAndFormatContext() {
        AiRagRetrievalAdapter retrievalAdapter = mock(AiRagRetrievalAdapter.class);
        AiRagOrchestrationService service = new AiRagOrchestrationService(retrievalAdapter, new AiRagContextFormatter());
        when(retrievalAdapter.retrieve(eq("kb-1"), eq("最近7天订单数"), eq(5))).thenReturn(List.of(
                KnowledgeChunkDTO.builder().docTitle("规则").content("只统计支付成功订单").build()
        ));

        AiRagResult result = service.retrieve(AiRagQuery.builder()
                .knowledgeBaseId("kb-1")
                .queryText("最近7天订单数")
                .topK(0)
                .scene(AiRagScene.SQL_PROMPT)
                .build());

        assertTrue(result.hasResults());
        assertEquals("Additional business context (use only if relevant):\n规则: 只统计支付成功订单", result.getFormattedContext());
        verify(retrievalAdapter).retrieve("kb-1", "最近7天订单数", 5);
    }

    @Test
    void shouldReturnEmptyWhenNoChunksRetrieved() {
        AiRagRetrievalAdapter retrievalAdapter = mock(AiRagRetrievalAdapter.class);
        AiRagOrchestrationService service = new AiRagOrchestrationService(retrievalAdapter, new AiRagContextFormatter());
        when(retrievalAdapter.retrieve(eq("kb-1"), eq("最近7天订单数"), eq(3))).thenReturn(List.of());

        AiRagResult result = service.retrieve(AiRagQuery.builder()
                .knowledgeBaseId("kb-1")
                .queryText("最近7天订单数")
                .topK(3)
                .scene(AiRagScene.SQL_PROMPT)
                .build());

        assertFalse(result.hasResults());
        assertFalse(result.isDegraded());
    }

    @Test
    void shouldDegradeWhenRetrievalFails() {
        AiRagRetrievalAdapter retrievalAdapter = mock(AiRagRetrievalAdapter.class);
        AiRagOrchestrationService service = new AiRagOrchestrationService(retrievalAdapter, new AiRagContextFormatter());
        when(retrievalAdapter.retrieve(eq("kb-1"), eq("最近7天订单数"), eq(5))).thenThrow(new RuntimeException("rag failed"));

        AiRagResult result = service.retrieve(AiRagQuery.builder()
                .knowledgeBaseId("kb-1")
                .queryText("最近7天订单数")
                .topK(5)
                .scene(AiRagScene.SQL_PROMPT)
                .build());

        assertFalse(result.hasResults());
        assertTrue(result.isDegraded());
    }
}
