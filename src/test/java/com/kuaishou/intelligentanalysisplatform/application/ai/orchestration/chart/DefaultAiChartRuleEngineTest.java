package com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.chart;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.contract.enums.FieldSemanticType;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ValueType;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.ChartRecommendationDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultAiChartRuleEngineTest {

    private final DefaultAiChartRuleEngine engine = new DefaultAiChartRuleEngine();

    @Test
    void shouldRecommendLineForTimeAndMetric() {
        List<ChartRecommendationDTO> result = engine.recommend(List.of(
                field("event_date", ValueType.STRING, FieldSemanticType.TIME_DIMENSION),
                field("amount", ValueType.DECIMAL, FieldSemanticType.METRIC)));

        assertEquals(1, result.size());
        assertEquals("LINE", result.get(0).getChartType());
        assertEquals("event_date", result.get(0).getFieldMapping().get("x"));
        assertEquals("amount", result.get(0).getFieldMapping().get("y"));
    }

    @Test
    void shouldRecommendPieForRatioMetric() {
        List<ChartRecommendationDTO> result = engine.recommend(List.of(
                field("category", ValueType.STRING, FieldSemanticType.DIMENSION),
                field("success_rate", ValueType.DECIMAL, FieldSemanticType.METRIC)));

        assertEquals(1, result.size());
        assertEquals("PIE", result.get(0).getChartType());
    }

    @Test
    void shouldRecommendBarForDimensionAndMetric() {
        List<ChartRecommendationDTO> result = engine.recommend(List.of(
                field("category", ValueType.STRING, FieldSemanticType.DIMENSION),
                field("count", ValueType.LONG, FieldSemanticType.METRIC)));

        assertEquals(1, result.size());
        assertEquals("BAR", result.get(0).getChartType());
    }

    @Test
    void shouldRecommendScatterForMultipleNumericFields() {
        List<ChartRecommendationDTO> result = engine.recommend(List.of(
                field("x_metric", ValueType.INTEGER, FieldSemanticType.DIMENSION),
                field("y_metric", ValueType.DECIMAL, FieldSemanticType.DIMENSION)));

        assertEquals(1, result.size());
        assertEquals("SCATTER", result.get(0).getChartType());
    }

    @Test
    void shouldReturnEmptyWhenNoRuleMatches() {
        List<ChartRecommendationDTO> result = engine.recommend(List.of(
                field("name", ValueType.STRING, FieldSemanticType.DIMENSION)));

        assertTrue(result.isEmpty());
    }

    private FieldSchemaDTO field(String name, ValueType valueType, FieldSemanticType semanticType) {
        return FieldSchemaDTO.builder()
                .name(name)
                .valueType(valueType)
                .semanticType(semanticType)
                .build();
    }
}
