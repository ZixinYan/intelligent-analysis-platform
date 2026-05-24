package com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.impl;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.AiChartOrchestrationService;
import com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.chart.AiChartLlmRecommendationService;
import com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.chart.AiChartRuleEngine;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiChartRecommendRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.ChartRecommendationDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DefaultAiChartOrchestrationService implements AiChartOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiChartOrchestrationService.class);
    private final boolean ruleSwitch = true;

    private final AiChartRuleEngine aiChartRuleEngine;
    private final AiChartLlmRecommendationService aiChartLlmRecommendationService;

    public DefaultAiChartOrchestrationService(AiChartRuleEngine aiChartRuleEngine,
                                              AiChartLlmRecommendationService aiChartLlmRecommendationService) {
        this.aiChartRuleEngine = aiChartRuleEngine;
        this.aiChartLlmRecommendationService = aiChartLlmRecommendationService;
    }

    @Override
    public List<ChartRecommendationDTO> recommend(AiChartRecommendRequestDTO request) {
        if (request.getFields() == null || request.getFields().isEmpty()) {
            return List.of();
        }

        if (ruleSwitch) {
            List<ChartRecommendationDTO> ruleResults = aiChartRuleEngine.recommend(request.getFields());
            if (!ruleResults.isEmpty()) {
                return ruleResults;
            }
        }

        try {
            return aiChartLlmRecommendationService.recommend(request.getFields());
        } catch (Exception e) {
            log.warn("LLM chart recommendation failed, returning default BAR", e);
            return List.of(ChartRecommendationDTO.builder()
                    .chartType("BAR")
                    .confidence(0.5f)
                    .reason("自动推荐失败，默认使用柱状图。")
                    .build());
        }
    }
}
