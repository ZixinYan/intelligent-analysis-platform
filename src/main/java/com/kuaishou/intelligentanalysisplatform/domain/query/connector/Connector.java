package com.kuaishou.intelligentanalysisplatform.domain.query.connector;

import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceType;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.domain.datasource.AnalysisDatasource;

public interface Connector {
    DatasourceType type();

    QueryResult execute(AnalysisDatasource datasource, QueryCommand command);

    java.util.List<FieldSchemaDTO> inferSchema(AnalysisDatasource datasource, QueryCommand command);

    java.util.List<String> listTables(AnalysisDatasource datasource);
}
