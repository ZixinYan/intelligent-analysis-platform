package com.kuaishou.intelligentanalysisplatform.application.ai.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.application.ai.AiChartRecommendationService;
import com.kuaishou.intelligentanalysisplatform.application.ai.AiProviderClient;
import com.kuaishou.intelligentanalysisplatform.contract.enums.FieldSemanticType;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ValueType;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiChartRecommendRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.ChartRecommendationDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DefaultAiChartRecommendationService implements AiChartRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiChartRecommendationService.class);
    private boolean RULE_SWITCH = true; // 规则引擎开关，方便后续逐步迁移和 A/B 测试

    private final AiProviderClient aiProviderClient;
    private final ObjectMapper objectMapper;

    public DefaultAiChartRecommendationService(AiProviderClient aiProviderClient,
                                               ObjectMapper objectMapper) {
        this.aiProviderClient = aiProviderClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ChartRecommendationDTO> recommend(AiChartRecommendRequestDTO request) {
        List<FieldSchemaDTO> fields = request.getFields();
        if (fields == null || fields.isEmpty()) return List.of();

        // 1. 规则引擎（快速，无 LLM）
        if (RULE_SWITCH) {
            List<ChartRecommendationDTO> ruleResults = applyRules(fields);
            if (!ruleResults.isEmpty()) {
                return ruleResults;
            }
        }

        // 2. LLM 兜底
        return fallbackToLlm(fields, request.getContext());
    }

    private List<ChartRecommendationDTO> applyRules(List<FieldSchemaDTO> fields) {
        List<FieldSchemaDTO> dateFields = filterBySemanticType(fields, FieldSemanticType.TIME_DIMENSION);
        List<FieldSchemaDTO> metricFields = filterBySemanticType(fields, FieldSemanticType.METRIC);
        List<FieldSchemaDTO> dimensionFields = filterBySemanticType(fields, FieldSemanticType.DIMENSION);
        List<FieldSchemaDTO> numericFields = filterNumeric(fields);

        // 规则 1：时间字段 + ≥1 指标 → LINE
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

        // 规则 2：1 维度 + 1 含占比语义指标 → PIE（必须先于 BAR 检查）
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

        // 规则 3：1 维度 + 1 指标 → BAR
        if (dimensionFields.size() == 1 && metricFields.size() == 1) {
            return List.of(ChartRecommendationDTO.builder()
                    .chartType("BAR")
                    .confidence(0.85f)
                    .reason("1 个维度字段 + 1 个指标字段，柱状图便于对比各类别数值。")
                    .fieldMapping(Map.of("x", dimensionFields.get(0).getName(), "y", metricFields.get(0).getName()))
                    .build());
        }

        // 规则 4：2 数值字段 → SCATTER
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

    private List<ChartRecommendationDTO> fallbackToLlm(List<FieldSchemaDTO> fields, String context) {
        String fieldsDesc = fields.stream()
                .map(f -> "- " + f.getName()
                        + " type=" + (f.getValueType() != null ? f.getValueType().name() : "?")
                        + (f.getSemanticType() != null ? " semantic=" + f.getSemanticType().name() : ""))
                .collect(Collectors.joining("\n"));
        String prompt = PromptLoader.load("chart-recommendation.txt", Map.of("FIELDS", fieldsDesc));
        try {
            String response = aiProviderClient.completion("", prompt);
            String json = LlmResponseParser.extractJson(response);
            ChartRecommendationDTO dto = objectMapper.readValue(json, ChartRecommendationDTO.class);
            return List.of(dto);
        } catch (Exception e) {
            log.warn("LLM chart recommendation failed, returning default BAR", e);
            return List.of(ChartRecommendationDTO.builder()
                    .chartType("BAR")
                    .confidence(0.5f)
                    .reason("自动推荐失败，默认使用柱状图。")
                    .build());
        }
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
