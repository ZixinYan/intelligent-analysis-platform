package com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.impl;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.chart.AiChartLlmRecommendationService;
import com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.chart.AiChartRuleEngine;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiChartRecommendRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.ChartRecommendationDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DefaultAiChartOrchestrationServiceTest {

    @Test
    void shouldReturnEmptyWhenFieldsMissing() {
        AiChartRuleEngine ruleEngine = mock(AiChartRuleEngine.class);
        AiChartLlmRecommendationService llmService = mock(AiChartLlmRecommendationService.class);
        DefaultAiChartOrchestrationService service = new DefaultAiChartOrchestrationService(ruleEngine, llmService);
        AiChartRecommendRequestDTO request = new AiChartRecommendRequestDTO();
        request.setFields(List.of());

        List<ChartRecommendationDTO> result = service.recommend(request);

        assertTrue(result.isEmpty());
        verifyNoInteractions(ruleEngine, llmService);
    }

    @Test
    void shouldReturnRuleResultWhenMatched() {
        AiChartRuleEngine ruleEngine = mock(AiChartRuleEngine.class);
        AiChartLlmRecommendationService llmService = mock(AiChartLlmRecommendationService.class);
        DefaultAiChartOrchestrationService service = new DefaultAiChartOrchestrationService(ruleEngine, llmService);
        AiChartRecommendRequestDTO request = new AiChartRecommendRequestDTO();
        request.setFields(List.of(FieldSchemaDTO.builder().name("category").build()));
        List<ChartRecommendationDTO> ruleResult = List.of(ChartRecommendationDTO.builder().chartType("BAR").build());

        when(ruleEngine.recommend(request.getFields())).thenReturn(ruleResult);

        List<ChartRecommendationDTO> result = service.recommend(request);

        assertEquals(ruleResult, result);
        verifyNoInteractions(llmService);
    }

    @Test
    void shouldFallbackToLlmWhenRuleMissed() {
        AiChartRuleEngine ruleEngine = mock(AiChartRuleEngine.class);
        AiChartLlmRecommendationService llmService = mock(AiChartLlmRecommendationService.class);
        DefaultAiChartOrchestrationService service = new DefaultAiChartOrchestrationService(ruleEngine, llmService);
        AiChartRecommendRequestDTO request = new AiChartRecommendRequestDTO();
        request.setFields(List.of(FieldSchemaDTO.builder().name("category").build()));
        List<ChartRecommendationDTO> llmResult = List.of(ChartRecommendationDTO.builder().chartType("LINE").build());

        when(ruleEngine.recommend(request.getFields())).thenReturn(List.of());
        when(llmService.recommend(request.getFields())).thenReturn(llmResult);

        List<ChartRecommendationDTO> result = service.recommend(request);

        assertEquals(llmResult, result);
    }

    @Test
    void shouldReturnDefaultBarWhenLlmFails() {
        AiChartRuleEngine ruleEngine = mock(AiChartRuleEngine.class);
        AiChartLlmRecommendationService llmService = mock(AiChartLlmRecommendationService.class);
        DefaultAiChartOrchestrationService service = new DefaultAiChartOrchestrationService(ruleEngine, llmService);
        AiChartRecommendRequestDTO request = new AiChartRecommendRequestDTO();
        request.setFields(List.of(FieldSchemaDTO.builder().name("category").build()));

        when(ruleEngine.recommend(request.getFields())).thenReturn(List.of());
        when(llmService.recommend(request.getFields())).thenThrow(new RuntimeException("llm failed"));

        List<ChartRecommendationDTO> result = service.recommend(request);

        assertEquals(1, result.size());
        assertEquals("BAR", result.get(0).getChartType());
        assertEquals(0.5f, result.get(0).getConfidence());
    }
}
