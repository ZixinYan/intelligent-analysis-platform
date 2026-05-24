package com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.chart;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.application.ai.foundation.parser.LlmOutputParser;
import com.kuaishou.intelligentanalysisplatform.application.ai.foundation.prompt.PromptTemplateService;
import com.kuaishou.intelligentanalysisplatform.application.ai.foundation.provider.AiModelProvider;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.ChartRecommendationDTO;
import org.springframework.stereotype.Service;

@Service
public class DefaultAiChartLlmRecommendationService implements AiChartLlmRecommendationService {

    private final AiModelProvider aiModelProvider;
    private final PromptTemplateService promptTemplateService;
    private final LlmOutputParser llmOutputParser;
    private final ObjectMapper objectMapper;

    public DefaultAiChartLlmRecommendationService(AiModelProvider aiModelProvider,
                                                  PromptTemplateService promptTemplateService,
                                                  LlmOutputParser llmOutputParser,
                                                  ObjectMapper objectMapper) {
        this.aiModelProvider = aiModelProvider;
        this.promptTemplateService = promptTemplateService;
        this.llmOutputParser = llmOutputParser;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ChartRecommendationDTO> recommend(List<FieldSchemaDTO> fields) {
        try {
            String fieldsDesc = fields.stream()
                    .map(f -> "- " + f.getName()
                            + " type=" + (f.getValueType() != null ? f.getValueType().name() : "?")
                            + (f.getSemanticType() != null ? " semantic=" + f.getSemanticType().name() : ""))
                    .collect(Collectors.joining("\n"));
            String prompt = promptTemplateService.load("chart-recommendation.txt", Map.of("FIELDS", fieldsDesc));
            String response = aiModelProvider.completeChat(
                    new AiModelProvider.AiChatRequest("", prompt, List.of()));
            String json = llmOutputParser.extractJson(response);
            ChartRecommendationDTO dto = objectMapper.readValue(json, ChartRecommendationDTO.class);
            return List.of(dto);
        } catch (Exception e) {
            throw new IllegalStateException("chart recommendation llm fallback failed", e);
        }
    }
}
