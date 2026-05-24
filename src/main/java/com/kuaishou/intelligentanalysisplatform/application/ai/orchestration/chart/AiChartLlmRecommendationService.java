package com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.chart;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.ChartRecommendationDTO;

public interface AiChartLlmRecommendationService {

    List<ChartRecommendationDTO> recommend(List<FieldSchemaDTO> fields);
}
