package com.kuaishou.intelligentanalysisplatform.application.ai;

import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiChartRecommendRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.ChartRecommendationDTO;

import java.util.List;

/**
 * AI 图表类型推荐服务。
 * 先走规则引擎（快速、无需调 LLM），未命中则 LLM 兜底。
 */
public interface AiChartRecommendationService {
    List<ChartRecommendationDTO> recommend(AiChartRecommendRequestDTO request);
}
