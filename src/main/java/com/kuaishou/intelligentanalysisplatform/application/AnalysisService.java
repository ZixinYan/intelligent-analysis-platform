package com.kuaishou.intelligentanalysisplatform.application;

import com.kuaishou.intelligentanalysisplatform.contract.schema.AsyncSubmitResponseDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SchemaInferResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ValidateResultDTO;

public interface AnalysisService {
    ValidateResultDTO validate(QueryRequestDTO request);

    QueryResultDTO preview(QueryRequestDTO request);

    AsyncSubmitResponseDTO run(QueryRequestDTO request);

    void cancel(String queryId);

    QueryResultDTO getStatus(String queryId);

    SchemaInferResultDTO inferSchema(QueryRequestDTO request);
}
