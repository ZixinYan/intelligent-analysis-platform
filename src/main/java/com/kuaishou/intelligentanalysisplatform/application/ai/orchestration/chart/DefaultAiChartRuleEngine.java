package com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.chart;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.kuaishou.intelligentanalysisplatform.contract.enums.FieldSemanticType;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ValueType;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.ChartRecommendationDTO;
import org.springframework.stereotype.Component;

@Component
public class DefaultAiChartRuleEngine implements AiChartRuleEngine {

    @Override
    public List<ChartRecommendationDTO> recommend(List<FieldSchemaDTO> fields) {
        if (fields == null || fields.isEmpty()) {
            return List.of();
        }

        List<FieldSchemaDTO> dateFields = filterBySemanticType(fields, FieldSemanticType.TIME_DIMENSION);
        List<FieldSchemaDTO> metricFields = filterBySemanticType(fields, FieldSemanticType.METRIC);
        List<FieldSchemaDTO> dimensionFields = filterBySemanticType(fields, FieldSemanticType.DIMENSION);
        List<FieldSchemaDTO> numericFields = filterNumeric(fields);

        if (!dateFields.isEmpty() && !metricFields.isEmpty()) {
            String xField = dateFields.get(0).getName();
            String yField = metricFields.get(0).getName();
            return List.of(ChartRecommendationDTO.builder()
                    .chartType("LINE")
                    .confidence(0.90f)
                    .reason("包含时间维度字段「" + xField + "」和指标字段「" + yField + "」，折线图最适合展示时间趋势。")
                    .fieldMapping(Map.of("x", xField, "y", yField))
                    .build());
        }

        if (dimensionFields.size() == 1 && metricFields.size() == 1) {
            String metricName = metricFields.get(0).getName().toLowerCase();
            if (metricName.contains("rate") || metricName.contains("ratio") || metricName.contains("percent")) {
                return List.of(ChartRecommendationDTO.builder()
                        .chartType("PIE")
                        .confidence(0.85f)
                        .reason("指标名称含占比语义，饼图适合展示各部分占比关系。")
                        .fieldMapping(Map.of("x", dimensionFields.get(0).getName(), "y", metricFields.get(0).getName()))
                        .build());
            }
        }

        if (dimensionFields.size() == 1 && metricFields.size() == 1) {
            return List.of(ChartRecommendationDTO.builder()
                    .chartType("BAR")
                    .confidence(0.85f)
                    .reason("1 个维度字段 + 1 个指标字段，柱状图便于对比各类别数值。")
                    .fieldMapping(Map.of("x", dimensionFields.get(0).getName(), "y", metricFields.get(0).getName()))
                    .build());
        }

        if (numericFields.size() >= 2) {
            return List.of(ChartRecommendationDTO.builder()
                    .chartType("SCATTER")
                    .confidence(0.75f)
                    .reason("存在多个数值字段，散点图可展示字段间的相关性分布。")
                    .fieldMapping(Map.of("x", numericFields.get(0).getName(), "y", numericFields.get(1).getName()))
                    .build());
        }

        return List.of();
    }

    private List<FieldSchemaDTO> filterBySemanticType(List<FieldSchemaDTO> fields, FieldSemanticType type) {
        return fields.stream()
                .filter(f -> type.equals(f.getSemanticType()))
                .collect(Collectors.toList());
    }

    private List<FieldSchemaDTO> filterNumeric(List<FieldSchemaDTO> fields) {
        return fields.stream()
                .filter(f -> f.getValueType() != null && isNumeric(f.getValueType()))
                .collect(Collectors.toList());
    }

    private boolean isNumeric(ValueType type) {
        return type == ValueType.INTEGER || type == ValueType.LONG || type == ValueType.DECIMAL;
    }
}
