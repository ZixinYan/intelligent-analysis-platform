package com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.chart;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.application.ai.foundation.parser.LlmOutputParser;
import com.kuaishou.intelligentanalysisplatform.application.ai.foundation.prompt.PromptTemplateService;
import com.kuaishou.intelligentanalysisplatform.application.ai.foundation.provider.AiModelProvider;
import com.kuaishou.intelligentanalysisplatform.contract.enums.FieldSemanticType;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ValueType;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.ChartRecommendationDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultAiChartLlmRecommendationServiceTest {

    @Test
    void shouldRecommendFromLlmResponse() {
        AiModelProvider aiModelProvider = mock(AiModelProvider.class);
        PromptTemplateService promptTemplateService = mock(PromptTemplateService.class);
        LlmOutputParser llmOutputParser = mock(LlmOutputParser.class);
        DefaultAiChartLlmRecommendationService service = new DefaultAiChartLlmRecommendationService(
                aiModelProvider, promptTemplateService, llmOutputParser, new ObjectMapper());
        List<FieldSchemaDTO> fields = List.of(field("category", ValueType.STRING, FieldSemanticType.DIMENSION));

        when(promptTemplateService.load(eq("chart-recommendation.txt"), any())).thenReturn("prompt");
        when(aiModelProvider.completeChat(eq(new AiModelProvider.AiChatRequest("", "prompt", List.of()))))
                .thenReturn("raw response");
        when(llmOutputParser.extractJson("raw response")).thenReturn("{\"chartType\":\"BAR\",\"confidence\":0.9,\"reason\":\"原因\",\"fieldMapping\":{\"x\":\"category\"}} ");

        List<ChartRecommendationDTO> result = service.recommend(fields);

        assertEquals(1, result.size());
        assertEquals("BAR", result.get(0).getChartType());
        assertEquals("category", result.get(0).getFieldMapping().get("x"));
        verify(promptTemplateService).load(eq("chart-recommendation.txt"), any(Map.class));
        verify(aiModelProvider).completeChat(eq(new AiModelProvider.AiChatRequest("", "prompt", List.of())));
    }

    @Test
    void shouldPropagateProviderException() {
        AiModelProvider aiModelProvider = mock(AiModelProvider.class);
        PromptTemplateService promptTemplateService = mock(PromptTemplateService.class);
        LlmOutputParser llmOutputParser = mock(LlmOutputParser.class);
        DefaultAiChartLlmRecommendationService service = new DefaultAiChartLlmRecommendationService(
                aiModelProvider, promptTemplateService, llmOutputParser, new ObjectMapper());

        when(promptTemplateService.load(eq("chart-recommendation.txt"), any())).thenReturn("prompt");
        when(aiModelProvider.completeChat(any())).thenThrow(new RuntimeException("llm failed"));

        assertThrows(RuntimeException.class, () -> service.recommend(List.of(field("category", ValueType.STRING, FieldSemanticType.DIMENSION))));
    }

    private FieldSchemaDTO field(String name, ValueType valueType, FieldSemanticType semanticType) {
        return FieldSchemaDTO.builder()
                .name(name)
                .valueType(valueType)
                .semanticType(semanticType)
                .build();
    }
}
