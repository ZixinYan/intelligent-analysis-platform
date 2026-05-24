package com.kuaishou.intelligentanalysisplatform.application.ai.orchestration;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiChartRecommendRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.ChartRecommendationDTO;

public interface AiChartOrchestrationService {

    List<ChartRecommendationDTO> recommend(AiChartRecommendRequestDTO request);
}
