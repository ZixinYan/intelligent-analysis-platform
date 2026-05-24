package com.kuaishou.intelligentanalysisplatform.application.ai.orchestration;

import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ai.AiSqlRequestDTO;

public interface AiSqlOrchestrationService {

    void generateSql(AiSqlRequestDTO request, RequestContextDTO context, AiStreamOutputHandler handler);
}
