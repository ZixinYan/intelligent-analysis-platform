package com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.chart;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.ChartRecommendationDTO;

public interface AiChartRuleEngine {

    List<ChartRecommendationDTO> recommend(List<FieldSchemaDTO> fields);
}
