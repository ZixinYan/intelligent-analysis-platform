package com.kuaishou.intelligentanalysisplatform.application.ai.rag;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.contract.schema.KnowledgeChunkDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRagContextFormatterTest {

    private final AiRagContextFormatter formatter = new AiRagContextFormatter();

    @Test
    void shouldFormatSqlPromptContext() {
        String context = formatter.format(List.of(
                KnowledgeChunkDTO.builder().docTitle("规则").content("只统计支付成功订单").build()
        ), AiRagScene.SQL_PROMPT);

        assertEquals("Additional business context (use only if relevant):\n规则: 只统计支付成功订单", context);
    }

    @Test
    void shouldFormatWorkflowContext() {
        String context = formatter.format(List.of(
                KnowledgeChunkDTO.builder().docTitle("规则").content("只统计支付成功订单").build(),
                KnowledgeChunkDTO.builder().docTitle("口径").content("金额单位为元").build()
        ), AiRagScene.WORKFLOW_CONTEXT);

        assertTrue(context.contains("【规则】\n只统计支付成功订单"));
        assertTrue(context.contains("【口径】\n金额单位为元"));
        assertTrue(context.contains("\n\n---\n\n"));
    }

    @Test
    void shouldReturnEmptyWhenChunksMissing() {
        assertTrue(formatter.format(List.of(), AiRagScene.SQL_PROMPT).isEmpty());
    }

    @Test
    void shouldFallbackUntitledWhenDocTitleMissing() {
        String context = formatter.format(List.of(
                KnowledgeChunkDTO.builder().content("上下文").build()
        ), AiRagScene.SQL_PROMPT);

        assertTrue(context.contains("Untitled: 上下文"));
        assertFalse(context.isBlank());
    }
}
