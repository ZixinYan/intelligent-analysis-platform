package com.kuaishou.intelligentanalysisplatform.infra.query.schema;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SchemaInferResultDTO;
import com.kuaishou.intelligentanalysisplatform.domain.datasource.AnalysisDatasource;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.Connector;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.ConnectorFactory;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.PaginationMode;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.QueryCommand;
import com.kuaishou.intelligentanalysisplatform.domain.query.schema.SchemaInferService;
import org.springframework.stereotype.Service;

@Service
public class JdbcSchemaInferService implements SchemaInferService {
    private final ConnectorFactory connectorFactory;

    public JdbcSchemaInferService(ConnectorFactory connectorFactory) {
        this.connectorFactory = connectorFactory;
    }

    @Override
    public SchemaInferResultDTO infer(AnalysisDatasource datasource, String sql, String queryId) {
        Connector connector = connectorFactory.create(datasource);
        List<FieldSchemaDTO> fields = connector.inferSchema(datasource, QueryCommand.builder()
                .queryId(queryId)
                .normalizedSql(sql)
                .paginationMode(PaginationMode.OFFSET)
                .pageSize(1)
                .maxRows(1)
                .build());
        Map<String, Object> summary = new HashMap<>();
        summary.put("fieldCount", fields.size());
        return SchemaInferResultDTO.builder()
                .protocolVersion("1.0")
                .schemaId(UUID.randomUUID().toString())
                .schemaVersion("1")
                .kind("tabular")
                .summary(summary)
                .fields(fields)
                .mappingHints(null)
                .rawSchema(Map.of())
                .extensions(Map.of())
                .build();
    }
}
