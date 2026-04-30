package com.kuaishou.intelligentanalysisplatform.domain.query.schema;

import com.kuaishou.intelligentanalysisplatform.contract.schema.SchemaInferResultDTO;
import com.kuaishou.intelligentanalysisplatform.domain.datasource.AnalysisDatasource;

public interface SchemaInferService {
    SchemaInferResultDTO infer(AnalysisDatasource datasource, String sql, String queryId);
}
