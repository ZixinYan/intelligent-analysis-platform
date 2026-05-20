package com.kuaishou.intelligentanalysisplatform.domain.query.connector;

import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceType;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.domain.datasource.AnalysisDatasource;

import java.util.Map;

public interface Connector {
    DatasourceType type();

    QueryResult execute(AnalysisDatasource datasource, QueryCommand command);

    java.util.List<FieldSchemaDTO> inferSchema(AnalysisDatasource datasource, QueryCommand command);

    java.util.List<String> listTables(AnalysisDatasource datasource);

    HealthCheckResult healthCheck(AnalysisDatasource datasource);

    /**
     * 获取指定表每个字段的备注（column comment），key 为字段名，value 为备注文本。
     * 不支持的数据库返回空 Map，不抛异常。
     */
    Map<String, String> listColumnComments(AnalysisDatasource datasource, String tableName);
}
