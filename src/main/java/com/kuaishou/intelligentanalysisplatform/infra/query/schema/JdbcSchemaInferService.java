package com.kuaishou.intelligentanalysisplatform.infra.query.schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kuaishou.intelligentanalysisplatform.contract.enums.FieldCapabilityTag;
import com.kuaishou.intelligentanalysisplatform.contract.enums.FieldSemanticType;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ValueType;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.MappingHintsDTO;
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
        List<FieldSchemaDTO> rawFields = connector.inferSchema(datasource, QueryCommand.builder()
                .queryId(queryId)
                .normalizedSql(sql)
                .paginationMode(PaginationMode.OFFSET)
                .pageSize(1)
                .maxRows(1)
                .build());
        List<FieldSchemaDTO> enrichedFields = enrichCapabilities(rawFields);
        Map<String, Object> summary = new HashMap<>();
        summary.put("fieldCount", enrichedFields.size());
        summary.put("hasTimeField", enrichedFields.stream().anyMatch(f ->
                f.getSemanticType() == FieldSemanticType.TIME_DIMENSION));
        summary.put("hasMetricField", enrichedFields.stream().anyMatch(f ->
                f.getSemanticType() == FieldSemanticType.METRIC));

        MappingHintsDTO mappingHints = buildMappingHints(enrichedFields);

        return SchemaInferResultDTO.builder()
                .protocolVersion("1.0")
                .schemaId(UUID.randomUUID().toString())
                .schemaVersion("1")
                .kind("tabular")
                .summary(summary)
                .fields(enrichedFields)
                .mappingHints(mappingHints)
                .rawSchema(Map.of())
                .extensions(Map.of())
                .build();
    }

    private List<FieldSchemaDTO> enrichCapabilities(List<FieldSchemaDTO> fields) {
        List<FieldSchemaDTO> result = new ArrayList<>();
        for (FieldSchemaDTO field : fields) {
            List<FieldCapabilityTag> capabilities = new ArrayList<>();
            capabilities.add(FieldCapabilityTag.SELECTABLE);
            capabilities.add(FieldCapabilityTag.TABLE_COLUMN_CANDIDATE);

            ValueType valueType = field.getValueType();
            FieldSemanticType semanticType = field.getSemanticType();

            if (semanticType == FieldSemanticType.TIME_DIMENSION) {
                capabilities.add(FieldCapabilityTag.X_AXIS_CANDIDATE);
                capabilities.add(FieldCapabilityTag.GROUPABLE);
                capabilities.add(FieldCapabilityTag.TIME_GRAIN_SUPPORTED);
            } else if (semanticType == FieldSemanticType.METRIC) {
                capabilities.add(FieldCapabilityTag.Y_AXIS_CANDIDATE);
                capabilities.add(FieldCapabilityTag.AGGREGATABLE);
            } else {
                capabilities.add(FieldCapabilityTag.GROUPABLE);
                capabilities.add(FieldCapabilityTag.SERIES_CANDIDATE);
                capabilities.add(FieldCapabilityTag.LABEL_CANDIDATE);
            }

            if (valueType != null && (valueType == ValueType.DATE || valueType == ValueType.DATETIME)) {
                if (!capabilities.contains(FieldCapabilityTag.X_AXIS_CANDIDATE)) {
                    capabilities.add(FieldCapabilityTag.X_AXIS_CANDIDATE);
                }
                capabilities.add(FieldCapabilityTag.GROUPABLE);
            }

            FieldSchemaDTO enriched = FieldSchemaDTO.builder()
                    .fieldId(field.getFieldId())
                    .name(field.getName())
                    .path(field.getPath())
                    .valueType(field.getValueType())
                    .nullable(field.getNullable())
                    .displayName(field.getDisplayName())
                    .semanticType(field.getSemanticType())
                    .capabilities(capabilities)
                    .sampleValues(field.getSampleValues())
                    .stats(field.getStats())
                    .extensions(field.getExtensions())
                    .build();
            result.add(enriched);
        }
        return result;
    }

    private MappingHintsDTO buildMappingHints(List<FieldSchemaDTO> fields) {
        List<String> timeFields = new ArrayList<>();
        List<String> metricFields = new ArrayList<>();
        List<String> dimFields = new ArrayList<>();

        for (FieldSchemaDTO field : fields) {
            FieldSemanticType st = field.getSemanticType();
            String name = field.getName();
            if (st == FieldSemanticType.TIME_DIMENSION) {
                timeFields.add(name);
            } else if (st == FieldSemanticType.METRIC) {
                metricFields.add(name);
            } else {
                dimFields.add(name);
            }
        }

        return MappingHintsDTO.builder()
                .chart(Map.of(
                        "xCandidates", (List<String>) (List<?>) timeFields,
                        "yCandidates", (List<String>) (List<?>) metricFields,
                        "seriesCandidates", (List<String>) (List<?>) dimFields))
                .table(Map.of("defaultColumns",
                        fields.stream().map(FieldSchemaDTO::getName).toList()))
                .build();
    }
}
