package com.kuaishou.intelligentanalysisplatform.application.impl;

import com.kuaishou.intelligentanalysisplatform.application.AnalysisService;
import com.kuaishou.intelligentanalysisplatform.application.QueryApplicationService;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AsyncSubmitResponseDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SchemaInferResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ValidateResultDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultAnalysisService implements AnalysisService {
    private final QueryApplicationService queryApplicationService;

    @Override
    public ValidateResultDTO validate(QueryRequestDTO request) {
        return queryApplicationService.validate(request);
    }

    @Override
    public QueryResultDTO preview(QueryRequestDTO request) {
        return queryApplicationService.preview(request);
    }

    @Override
    public AsyncSubmitResponseDTO run(QueryRequestDTO request) {
        return queryApplicationService.runAsync(request);
    }

    @Override
    public void cancel(String queryId) {
        queryApplicationService.cancel(queryId);
    }

    @Override
    public QueryResultDTO getStatus(String queryId) {
        return queryApplicationService.getStatus(queryId);
    }

    @Override
    public SchemaInferResultDTO inferSchema(QueryRequestDTO request) {
        return queryApplicationService.inferSchema(request);
    }
}
