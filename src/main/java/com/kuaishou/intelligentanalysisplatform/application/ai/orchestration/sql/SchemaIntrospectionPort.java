package com.kuaishou.intelligentanalysisplatform.application.ai.orchestration.sql;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;

/**
 * AI SQL 编排所需的数据源 Schema 查询端口。
 *
 * <p>通过窄接口隔离 AI 模块与 DatasourceApplicationService 的全量依赖，
 * 避免跨 application service 的直接引用。
 */
public interface SchemaIntrospectionPort {

    List<FieldSchemaDTO> introspectTableSchema(String datasourceId, String tableName, RequestContextDTO context);
}
