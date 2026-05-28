package com.kuaishou.intelligentanalysisplatform.application.impl;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.application.NodeMetadataApplicationService;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ConditionOperator;
import com.kuaishou.intelligentanalysisplatform.contract.enums.FieldCapabilityTag;
import com.kuaishou.intelligentanalysisplatform.contract.enums.FieldComponentType;
import com.kuaishou.intelligentanalysisplatform.contract.enums.FieldSemanticType;
import com.kuaishou.intelligentanalysisplatform.contract.enums.NodeCategory;
import com.kuaishou.intelligentanalysisplatform.contract.enums.NodeType;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ValueType;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldCandidateSlotDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldEnableRuleDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldMappingCandidateDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldVisibilityRuleDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.MappingHintsDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeCapabilityDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeConfigSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodePortMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.OptionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.OptionsSourceDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.PanelFieldDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.PanelSectionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SchemaInferResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ValidationRuleDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.VariableBindingSupportDTO;
import org.springframework.stereotype.Service;

@Service
public class DefaultNodeMetadataApplicationService implements NodeMetadataApplicationService {

    @Override
    public List<NodeMetaDTO> listNodeDefinitions() {
        return List.of(
                buildSqlQueryDefinition(),
                buildAggregateDefinition(),
                buildTimeSeriesComputeDefinition(),
                buildPivotDefinition(),
                buildFilterDefinition(),
                buildSortDefinition(),
                buildFormulaDefinition(),
                buildPythonScriptDefinition(),
                buildJavaCodeDefinition(),
                buildChartOutputDefinition(),
                buildTableOutputDefinition(),
                buildConditionDefinition(),
                buildErrorHandlerDefinition(),
                buildDataJoinDefinition());
    }

    @Override
    public NodeMetaDTO getNodeDefinition(String nodeType) {
        return listNodeDefinitions().stream()
                .filter(item -> item.getNodeType().equals(nodeType))
                .findFirst()
                .orElseThrow(() -> new BaseBusinessException(ErrorCode.NODE_NOT_FOUND, "node definition not found"));
    }

    @Override
    public SchemaInferResultDTO inferSchema(String nodeType) {
        if (!NodeType.SQL_QUERY.getCode().equals(nodeType)) {
            throw new BaseBusinessException(ErrorCode.NODE_NOT_FOUND, "schema infer not found");
        }
        return SchemaInferResultDTO.builder()
                .protocolVersion("1.0")
                .schemaId("schema_sql_query_demo")
                .schemaVersion("1.0")
                .kind("table")
                .summary(Map.of(
                        "rowCount", 500,
                        "fieldCount", 4,
                        "hasTimeField", true,
                        "hasMetricField", true))
                .fields(List.of(
                        FieldSchemaDTO.builder()
                                .fieldId("order_date")
                                .name("order_date")
                                .path(List.of("rows", "*", "order_date"))
                                .valueType(ValueType.DATE)
                                .nullable(false)
                                .displayName("下单日期")
                                .semanticType(FieldSemanticType.TIME_DIMENSION)
                                .capabilities(List.of(
                                        FieldCapabilityTag.SELECTABLE,
                                        FieldCapabilityTag.GROUPABLE,
                                        FieldCapabilityTag.X_AXIS_CANDIDATE,
                                        FieldCapabilityTag.TIME_GRAIN_SUPPORTED))
                                .sampleValues(List.of("2026-04-01", "2026-04-02"))
                                .stats(Map.of("min", "2026-04-01", "max", "2026-04-30"))
                                .build(),
                        FieldSchemaDTO.builder()
                                .fieldId("product_name")
                                .name("product_name")
                                .path(List.of("rows", "*", "product_name"))
                                .valueType(ValueType.STRING)
                                .nullable(false)
                                .displayName("商品名称")
                                .semanticType(FieldSemanticType.DIMENSION)
                                .capabilities(List.of(
                                        FieldCapabilityTag.SELECTABLE,
                                        FieldCapabilityTag.GROUPABLE,
                                        FieldCapabilityTag.SERIES_CANDIDATE,
                                        FieldCapabilityTag.TABLE_COLUMN_CANDIDATE))
                                .sampleValues(List.of("商品A", "商品B"))
                                .stats(Map.of("distinctCount", 20))
                                .build(),
                        FieldSchemaDTO.builder()
                                .fieldId("sales_amount")
                                .name("sales_amount")
                                .path(List.of("rows", "*", "sales_amount"))
                                .valueType(ValueType.DECIMAL)
                                .nullable(false)
                                .displayName("销售额")
                                .semanticType(FieldSemanticType.METRIC)
                                .capabilities(List.of(
                                        FieldCapabilityTag.SELECTABLE,
                                        FieldCapabilityTag.AGGREGATABLE,
                                        FieldCapabilityTag.Y_AXIS_CANDIDATE,
                                        FieldCapabilityTag.TABLE_COLUMN_CANDIDATE))
                                .sampleValues(List.of(1024.5, 2048.0))
                                .stats(Map.of("min", 12.3, "max", 9982.1))
                                .build(),
                        FieldSchemaDTO.builder()
                                .fieldId("order_count")
                                .name("order_count")
                                .path(List.of("rows", "*", "order_count"))
                                .valueType(ValueType.INTEGER)
                                .nullable(false)
                                .displayName("订单数")
                                .semanticType(FieldSemanticType.METRIC)
                                .capabilities(List.of(
                                        FieldCapabilityTag.SELECTABLE,
                                        FieldCapabilityTag.AGGREGATABLE,
                                        FieldCapabilityTag.Y_AXIS_CANDIDATE,
                                        FieldCapabilityTag.TABLE_COLUMN_CANDIDATE))
                                .sampleValues(List.of(10, 23))
                                .stats(Map.of("min", 1, "max", 100))
                                .build()))
                .mappingHints(MappingHintsDTO.builder()
                        .chart(Map.of(
                                "xCandidates", List.of("order_date"),
                                "yCandidates", List.of("sales_amount", "order_count"),
                                "seriesCandidates", List.of("product_name")))
                        .table(Map.of("defaultColumns", List.of("order_date", "product_name", "sales_amount")))
                        .build())
                .rawSchema(Map.of(
                        "columns", List.of(
                                Map.of("name", "order_date", "jdbcType", "DATE"),
                                Map.of("name", "product_name", "jdbcType", "VARCHAR"),
                                Map.of("name", "sales_amount", "jdbcType", "DECIMAL"),
                                Map.of("name", "order_count", "jdbcType", "INTEGER"))))
                .build();
    }

    @Override
    public List<FieldCandidateSlotDTO> getMappingCandidates(String nodeType, String renderer) {
        return getMappingCandidates(nodeType, renderer, null);
    }

    @Override
    public List<FieldCandidateSlotDTO> getMappingCandidates(String nodeType, String renderer, List<FieldSchemaDTO> upstreamFields) {
        boolean hasUpstreamFields = upstreamFields != null && !upstreamFields.isEmpty();

        if (NodeType.CHART_OUTPUT.getCode().equals(nodeType)) {
            return getChartMappingCandidates(renderer, hasUpstreamFields ? upstreamFields : null);
        }
        if (NodeType.TABLE_OUTPUT.getCode().equals(nodeType)) {
            if (renderer != null && !renderer.isBlank() && !"table".equalsIgnoreCase(renderer)) {
                throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "renderer is not supported for table_output");
            }
            if (hasUpstreamFields) {
                return List.of(buildSlotFromFields("columns", true, upstreamFields,
                        List.of("TABLE_COLUMN_CANDIDATE")));
            }
            return List.of(buildSlot("columns", true, List.of("ANY"),
                    List.of("TABLE_COLUMN_CANDIDATE"),
                    List.of(
                            candidate("order_date", 0.99, "table default column"),
                            candidate("product_name", 0.98, "table default column"),
                            candidate("sales_amount", 0.97, "table default column"),
                            candidate("order_count", 0.95, "table default column"))));
        }
        // Compute nodes: return upstream fields as candidates for each field picker slot
        if (NodeType.AGGREGATE.getCode().equals(nodeType)
                || NodeType.FILTER.getCode().equals(nodeType)
                || NodeType.SORT.getCode().equals(nodeType)
                || NodeType.FORMULA.getCode().equals(nodeType)
                || NodeType.PIVOT.getCode().equals(nodeType)
                || NodeType.TIME_SERIES_COMPUTE.getCode().equals(nodeType)) {
            if (hasUpstreamFields) {
                return buildComputeNodeCandidates(nodeType, upstreamFields);
            }
            return List.of();
        }
        throw new BaseBusinessException(ErrorCode.NODE_NOT_FOUND, "mapping candidates not found");
    }

    private List<FieldCandidateSlotDTO> getChartMappingCandidates(String renderer) {
        return getChartMappingCandidates(renderer, null);
    }

    private List<FieldCandidateSlotDTO> getChartMappingCandidates(String renderer, List<FieldSchemaDTO> upstreamFields) {
        if (renderer == null || renderer.isBlank()) {
            throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "renderer is required for chart_output");
        }
        String normalizedRenderer = renderer.toLowerCase();
        boolean hasUpstream = upstreamFields != null && !upstreamFields.isEmpty();

        switch (normalizedRenderer) {
            case "line":
            case "bar":
            case "area":
                return List.of(
                        buildSlotOrDynamic("xField", true, List.of("DATE", "DATETIME", "STRING"),
                                List.of("X_AXIS_CANDIDATE"), upstreamFields,
                                List.of(candidate("order_date", 0.98, "semanticType=time_dimension"))),
                        buildSlotOrDynamic("yField", true, List.of("DECIMAL", "INTEGER", "LONG"),
                                List.of("Y_AXIS_CANDIDATE", "AGGREGATABLE"), upstreamFields,
                                List.of(
                                        candidate("sales_amount", 0.97, "semanticType=metric"),
                                        candidate("order_count", 0.91, "semanticType=metric"))),
                        buildSlotOrDynamic("seriesField", false, List.of("STRING"),
                                List.of("SERIES_CANDIDATE"), upstreamFields,
                                List.of(candidate("product_name", 0.90, "semanticType=dimension"))));
            case "scatter":
                return List.of(
                        buildSlotOrDynamic("xField", true, List.of("DECIMAL", "INTEGER", "LONG"),
                                List.of("X_AXIS_CANDIDATE", "AGGREGATABLE"), upstreamFields,
                                List.of(candidate("order_count", 0.93, "numeric scatter axis"))),
                        buildSlotOrDynamic("yField", true, List.of("DECIMAL", "INTEGER", "LONG"),
                                List.of("Y_AXIS_CANDIDATE", "AGGREGATABLE"), upstreamFields,
                                List.of(candidate("sales_amount", 0.97, "numeric scatter axis"))),
                        buildSlotOrDynamic("seriesField", false, List.of("STRING"),
                                List.of("SERIES_CANDIDATE"), upstreamFields,
                                List.of(candidate("product_name", 0.88, "scatter grouping"))));
            case "pie":
                return List.of(
                        buildSlotOrDynamic("categoryField", true, List.of("STRING", "DATE"),
                                List.of("LABEL_CANDIDATE", "GROUPABLE"), upstreamFields,
                                List.of(candidate("product_name", 0.96, "pie label field"))),
                        buildSlotOrDynamic("valueField", true, List.of("DECIMAL", "INTEGER", "LONG"),
                                List.of("Y_AXIS_CANDIDATE", "AGGREGATABLE"), upstreamFields,
                                List.of(candidate("sales_amount", 0.98, "pie value field"))));
            default:
                throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "renderer is not supported for chart_output");
        }
    }

    private FieldCandidateSlotDTO buildSlotOrDynamic(String slot, Boolean required, List<String> acceptedTypes,
                                                      List<String> acceptedCapabilities,
                                                      List<FieldSchemaDTO> upstreamFields,
                                                      List<FieldMappingCandidateDTO> fallbackCandidates) {
        if (upstreamFields != null && !upstreamFields.isEmpty()) {
            return buildSlotFromFields(slot, required, upstreamFields, acceptedCapabilities);
        }
        return buildSlot(slot, required, acceptedTypes, acceptedCapabilities, fallbackCandidates);
    }

    private FieldCandidateSlotDTO buildSlotFromFields(String slot, Boolean required,
                                                       List<FieldSchemaDTO> upstreamFields,
                                                       List<String> acceptedCapabilities) {
        List<FieldMappingCandidateDTO> candidates = upstreamFields.stream()
                .filter(field -> matchesCapability(field, acceptedCapabilities))
                .map(field -> candidate(field.getName(), scoreFromField(field, acceptedCapabilities),
                        "semanticType=" + (field.getSemanticType() == null ? "unknown" : field.getSemanticType().name())))
                .toList();
        return FieldCandidateSlotDTO.builder()
                .slot(slot)
                .required(required)
                .acceptedTypes(List.of("ANY"))
                .acceptedCapabilities(acceptedCapabilities)
                .candidates(candidates)
                .build();
    }

    private boolean matchesCapability(FieldSchemaDTO field, List<String> acceptedCapabilities) {
        if (acceptedCapabilities == null || acceptedCapabilities.isEmpty()) {
            return true;
        }
        if (field.getCapabilities() != null && !field.getCapabilities().isEmpty()) {
            for (String cap : acceptedCapabilities) {
                if (field.getCapabilities().stream().anyMatch(c -> c.name().equalsIgnoreCase(cap))) {
                    return true;
                }
            }
        }
        // Fallback: infer capabilities from valueType and semanticType
        for (String cap : acceptedCapabilities) {
            if (inferCapability(field, cap)) {
                return true;
            }
        }
        return field.getCapabilities() == null || field.getCapabilities().isEmpty();
    }

    private boolean inferCapability(FieldSchemaDTO field, String capability) {
        ValueType valueType = field.getValueType();
        FieldSemanticType semanticType = field.getSemanticType();
        return switch (capability) {
            case "X_AXIS_CANDIDATE" ->
                    valueType == ValueType.DATE || valueType == ValueType.DATETIME
                    || semanticType == FieldSemanticType.TIME_DIMENSION;
            case "Y_AXIS_CANDIDATE", "AGGREGATABLE" ->
                    valueType == ValueType.INTEGER || valueType == ValueType.LONG || valueType == ValueType.DECIMAL
                    || semanticType == FieldSemanticType.METRIC;
            case "SERIES_CANDIDATE", "LABEL_CANDIDATE", "GROUPABLE" ->
                    valueType == ValueType.STRING
                    || semanticType == FieldSemanticType.DIMENSION;
            case "TABLE_COLUMN_CANDIDATE", "SELECTABLE" -> true;
            default -> false;
        };
    }

    private double scoreFromField(FieldSchemaDTO field, List<String> acceptedCapabilities) {
        if (acceptedCapabilities == null || acceptedCapabilities.isEmpty()) {
            return 0.5;
        }
        FieldSemanticType semanticType = field.getSemanticType();
        ValueType valueType = field.getValueType();
        double score = 0.5;

        for (String cap : acceptedCapabilities) {
            switch (cap) {
                case "X_AXIS_CANDIDATE":
                    if (semanticType == FieldSemanticType.TIME_DIMENSION) score = Math.max(score, 0.95);
                    else if (valueType == ValueType.DATE || valueType == ValueType.DATETIME) score = Math.max(score, 0.85);
                    else score = Math.max(score, 0.3);
                    break;
                case "Y_AXIS_CANDIDATE":
                    if (semanticType == FieldSemanticType.METRIC) score = Math.max(score, 0.95);
                    else if (valueType == ValueType.DECIMAL || valueType == ValueType.INTEGER || valueType == ValueType.LONG)
                        score = Math.max(score, 0.85);
                    else score = Math.max(score, 0.2);
                    break;
                case "SERIES_CANDIDATE":
                    if (semanticType == FieldSemanticType.DIMENSION) score = Math.max(score, 0.90);
                    else if (valueType == ValueType.STRING) score = Math.max(score, 0.80);
                    else score = Math.max(score, 0.3);
                    break;
                case "TABLE_COLUMN_CANDIDATE":
                    score = Math.max(score, 0.7);
                    break;
                default:
                    score = Math.max(score, 0.5);
                    break;
            }
        }
        return score;
    }

    private NodeMetaDTO buildSqlQueryDefinition() {
        return NodeMetaDTO.builder()
                .protocolVersion("1.0")
                .metadataVersion("2026-04-29")
                .nodeType(NodeType.SQL_QUERY.getCode())
                .nodeVersion("1.0")
                .displayName("SQL Query")
                .category(NodeCategory.QUERY)
                .description("取数节点")
                .tags(List.of("sql", "query", "table"))
                .defaults(Map.of(
                        "timeoutMs", 10000,
                        "limit", 500,
                        "enableCache", true))
                .configSchema(NodeConfigSchemaDTO.builder()
                        .schemaType("panel")
                        .schemaVersion("1.0")
                        .panelId("analysis.sql-query")
                        .layout(Map.of("type", "section-list"))
                        .sections(List.of(
                                PanelSectionDTO.builder()
                                        .key("basic")
                                        .title("基础配置")
                                        .order(1)
                                        .fields(List.of(
                                                PanelFieldDTO.builder()
                                                        .field("datasourceId")
                                                        .label("数据源")
                                                        .componentType(FieldComponentType.SELECT)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(1)
                                                        .valueType(ValueType.STRING)
                                                        .semanticType(FieldSemanticType.DATASOURCE_ID)
                                                        .optionsSource(OptionsSourceDTO.builder()
                                                                .type("remote")
                                                                .uri("/api/v1/datasources/options")
                                                                .method("GET")
                                                                .valueField("value")
                                                                .labelField("label")
                                                                .build())
                                                        .variableBinding(VariableBindingSupportDTO.builder().enabled(false).allowLiteral(true).build())
                                                        .build(),
                                                PanelFieldDTO.builder()
                                                        .field("tableId")
                                                        .label("数据表")
                                                        .componentType(FieldComponentType.SELECT)
                                                        .required(false)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(2)
                                                        .valueType(ValueType.STRING)
                                                        .placeholder("选择数据源后可选择表（可选）")
                                                        .description("从已连接数据库中选择一张表，作为 SQL 编写参考")
                                                        .optionsSource(OptionsSourceDTO.builder()
                                                                .type("remote")
                                                                .uri("/api/v1/datasources/{datasourceId}/tables")
                                                                .method("GET")
                                                                .valueField("value")
                                                                .labelField("label")
                                                                .build())
                                                        .visibilityRules(List.of(
                                                                FieldVisibilityRuleDTO.builder()
                                                                        .watchField("datasourceId")
                                                                        .operator(ConditionOperator.IS_NOT_EMPTY)
                                                                        .targetValues(List.of())
                                                                        .visible(true)
                                                                        .build()))
                                                        .variableBinding(VariableBindingSupportDTO.builder().enabled(false).allowLiteral(true).build())
                                                        .build(),
                                                PanelFieldDTO.builder()
                                                        .field("sqlTemplate")
                                                        .label("SQL")
                                                        .componentType(FieldComponentType.SQL_EDITOR)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(3)
                                                        .valueType(ValueType.STRING)
                                                        .semanticType(FieldSemanticType.SQL)
                                                        .props(Map.of("language", "sql", "supportVariableInsert", true))
                                                        .validations(List.of(
                                                                ValidationRuleDTO.builder().type("required").message("SQL 不能为空").build(),
                                                                ValidationRuleDTO.builder().type("maxLength").maxLength(20000).message("SQL 长度不能超过 20000").build()))
                                                        .variableBinding(VariableBindingSupportDTO.builder().enabled(true).allowLiteral(true).bindingPathHint("result.rows").build())
                                                        .build()))
                                        .build(),
                                PanelSectionDTO.builder()
                                        .key("runtime")
                                        .title("运行设置")
                                        .order(2)
                                        .fields(List.of(
                                                PanelFieldDTO.builder()
                                                        .field("timeoutMs")
                                                        .label("超时(ms)")
                                                        .componentType(FieldComponentType.NUMBER_INPUT)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(1)
                                                        .valueType(ValueType.INTEGER)
                                                        .defaultValue(10000)
                                                        .validations(List.of(
                                                                ValidationRuleDTO.builder().type("min").min(1000).message("最小超时 1000ms").build(),
                                                                ValidationRuleDTO.builder().type("max").max(60000).message("最大超时 60000ms").build()))
                                                        .build(),
                                                PanelFieldDTO.builder()
                                                        .field("limit")
                                                        .label("返回行数")
                                                        .componentType(FieldComponentType.NUMBER_INPUT)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(2)
                                                        .valueType(ValueType.INTEGER)
                                                        .defaultValue(500)
                                                        .validations(List.of(
                                                                ValidationRuleDTO.builder().type("min").min(1).message("最小返回 1 行").build(),
                                                                ValidationRuleDTO.builder().type("max").max(5000).message("最大返回 5000 行").build()))
                                                        .build(),
                                                PanelFieldDTO.builder()
                                                        .field("enableCache")
                                                        .label("启用缓存")
                                                        .componentType(FieldComponentType.SWITCH)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(3)
                                                        .valueType(ValueType.BOOLEAN)
                                                        .defaultValue(true)
                                                        .build()))
                                        .build()))
                        .rules(List.of())
                        .build())
                .inputPorts(List.of(NodePortMetaDTO.builder()
                        .name("parameters")
                        .label("参数")
                        .valueType(ValueType.OBJECT)
                        .required(false)
                        .multiple(false)
                        .build()))
                .outputPorts(List.of(NodePortMetaDTO.builder()
                        .name("dataset")
                        .label("结果集")
                        .valueType(ValueType.DATASET)
                        .required(true)
                        .multiple(false)
                        .build()))
                .capabilities(List.of(
                        NodeCapabilityDTO.builder().code("SCHEMA_INFER").name("支持 schema 推断").build(),
                        NodeCapabilityDTO.builder().code("VARIABLE_SELECTOR").name("支持变量选择").build(),
                        NodeCapabilityDTO.builder().code("FIELD_MAPPING_SOURCE").name("支持字段映射源").build(),
                        NodeCapabilityDTO.builder().code("TABLE_OUTPUT_SOURCE").name("支持表格输出源").build()))
                .build();
    }

    private NodeMetaDTO buildChartOutputDefinition() {
        return NodeMetaDTO.builder()
                .protocolVersion("1.0")
                .metadataVersion("2026-04-29")
                .nodeType(NodeType.CHART_OUTPUT.getCode())
                .nodeVersion("1.0")
                .displayName("Chart Output")
                .category(NodeCategory.OUTPUT)
                .description("统一图表输出节点")
                .tags(List.of("chart", "output"))
                .defaults(Map.of("chartType", "line"))
                .configSchema(NodeConfigSchemaDTO.builder()
                        .schemaType("panel")
                        .schemaVersion("1.0")
                        .panelId("analysis.chart-output")
                        .layout(Map.of("type", "section-list"))
                        .sections(List.of(PanelSectionDTO.builder()
                                .key("basic")
                                .title("图表配置")
                                .order(1)
                                .fields(List.of(
                                        PanelFieldDTO.builder()
                                                .field("chartType")
                                                .label("图表类型")
                                                .componentType(FieldComponentType.SELECT)
                                                .required(true)
                                                .visible(true)
                                                .editable(true)
                                                .order(1)
                                                .valueType(ValueType.STRING)
                                                .semanticType(FieldSemanticType.CHART_TYPE)
                                                .defaultValue("line")
                                                .options(List.of(
                                                        OptionDTO.builder().label("折线图").value("line").build(),
                                                        OptionDTO.builder().label("柱状图").value("bar").build(),
                                                        OptionDTO.builder().label("饼图").value("pie").build(),
                                                        OptionDTO.builder().label("散点图").value("scatter").build(),
                                                        OptionDTO.builder().label("面积图").value("area").build()))
                                                .build(),
                                        PanelFieldDTO.builder()
                                                .field("title")
                                                .label("标题")
                                                .componentType(FieldComponentType.INPUT)
                                                .required(false)
                                                .visible(true)
                                                .editable(true)
                                                .order(2)
                                                .valueType(ValueType.STRING)
                                                .build(),
                                        PanelFieldDTO.builder()
                                                .field("xField")
                                                .label("X 轴字段")
                                                .componentType(FieldComponentType.FIELD_PICKER)
                                                .required(true)
                                                .visible(true)
                                                .editable(true)
                                                .order(3)
                                                .valueType(ValueType.STRING)
                                                .optionsSource(OptionsSourceDTO.builder()
                                                        .type("schema-fields")
                                                        .source("upstream")
                                                        .acceptedCapabilities(List.of("X_AXIS_CANDIDATE"))
                                                        .build())
                                                .build(),
                                        PanelFieldDTO.builder()
                                                .field("yField")
                                                .label("Y 轴字段")
                                                .componentType(FieldComponentType.FIELD_PICKER)
                                                .required(true)
                                                .visible(true)
                                                .editable(true)
                                                .order(4)
                                                .valueType(ValueType.STRING)
                                                .optionsSource(OptionsSourceDTO.builder()
                                                        .type("schema-fields")
                                                        .source("upstream")
                                                        .acceptedCapabilities(List.of("Y_AXIS_CANDIDATE"))
                                                        .build())
                                                .build(),
                                        PanelFieldDTO.builder()
                                                .field("seriesField")
                                                .label("系列字段")
                                                .componentType(FieldComponentType.FIELD_PICKER)
                                                .required(false)
                                                .visible(true)
                                                .editable(true)
                                                .order(5)
                                                .valueType(ValueType.STRING)
                                                .optionsSource(OptionsSourceDTO.builder()
                                                        .type("schema-fields")
                                                        .source("upstream")
                                                        .acceptedCapabilities(List.of("SERIES_CANDIDATE"))
                                                        .build())
                                                .visibilityRules(List.of(FieldVisibilityRuleDTO.builder()
                                                        .watchField("chartType")
                                                        .operator(ConditionOperator.NOT_IN)
                                                        .targetValues(List.of("pie"))
                                                        .visible(true)
                                                        .build()))
                                                .build()))
                                .build()))
                        .rules(List.of())
                        .build())
                .inputPorts(List.of(NodePortMetaDTO.builder()
                        .name("dataset")
                        .label("数据集")
                        .valueType(ValueType.DATASET)
                        .required(true)
                        .multiple(false)
                        .build()))
                .outputPorts(List.of(NodePortMetaDTO.builder()
                        .name("chart")
                        .label("图表输出")
                        .valueType(ValueType.CHART)
                        .required(true)
                        .multiple(false)
                        .build()))
                .capabilities(List.of(
                        NodeCapabilityDTO.builder().code("FIELD_MAPPING_TARGET").name("支持字段映射目标").build(),
                        NodeCapabilityDTO.builder().code("CHART_OUTPUT_TARGET").name("支持图表输出").build(),
                        NodeCapabilityDTO.builder().code("PREVIEW_SUPPORTED").name("支持预览").build()))
                .build();
    }

    private NodeMetaDTO buildTableOutputDefinition() {
        return NodeMetaDTO.builder()
                .protocolVersion("1.0")
                .metadataVersion("2026-04-29")
                .nodeType(NodeType.TABLE_OUTPUT.getCode())
                .nodeVersion("1.0")
                .displayName("Table Output")
                .category(NodeCategory.OUTPUT)
                .description("统一表格输出节点")
                .tags(List.of("table", "output"))
                .defaults(Map.of("pageable", true, "pageSize", 20))
                .configSchema(NodeConfigSchemaDTO.builder()
                        .schemaType("panel")
                        .schemaVersion("1.0")
                        .panelId("analysis.table-output")
                        .layout(Map.of("type", "section-list"))
                        .sections(List.of(PanelSectionDTO.builder()
                                .key("basic")
                                .title("表格配置")
                                .order(1)
                                .fields(List.of(
                                        PanelFieldDTO.builder()
                                                .field("title")
                                                .label("标题")
                                                .componentType(FieldComponentType.INPUT)
                                                .required(false)
                                                .visible(true)
                                                .editable(true)
                                                .order(1)
                                                .valueType(ValueType.STRING)
                                                .build(),
                                        PanelFieldDTO.builder()
                                                .field("columns")
                                                .label("展示列")
                                                .componentType(FieldComponentType.FIELD_MULTI_SELECTOR)
                                                .required(true)
                                                .visible(true)
                                                .editable(true)
                                                .multiple(true)
                                                .order(2)
                                                .valueType(ValueType.ARRAY_STRING)
                                                .optionsSource(OptionsSourceDTO.builder()
                                                        .type("schema-fields")
                                                        .source("upstream")
                                                        .acceptedCapabilities(List.of("TABLE_COLUMN_CANDIDATE"))
                                                        .build())
                                                .build(),
                                        PanelFieldDTO.builder()
                                                .field("pageable")
                                                .label("分页")
                                                .componentType(FieldComponentType.SWITCH)
                                                .required(true)
                                                .visible(true)
                                                .editable(true)
                                                .order(3)
                                                .valueType(ValueType.BOOLEAN)
                                                .defaultValue(true)
                                                .build(),
                                        PanelFieldDTO.builder()
                                                .field("pageSize")
                                                .label("每页数量")
                                                .componentType(FieldComponentType.NUMBER_INPUT)
                                                .required(true)
                                                .visible(true)
                                                .editable(true)
                                                .order(4)
                                                .valueType(ValueType.INTEGER)
                                                .defaultValue(20)
                                                .enableRules(List.of(FieldEnableRuleDTO.builder()
                                                        .watchField("pageable")
                                                        .operator(ConditionOperator.EQ)
                                                        .targetValues(List.of(true))
                                                        .enabled(true)
                                                        .build()))
                                                .validations(List.of(
                                                        ValidationRuleDTO.builder().type("min").min(5).message("每页最少 5 条").build(),
                                                        ValidationRuleDTO.builder().type("max").max(200).message("每页最多 200 条").build()))
                                                .build()))
                                .build()))
                        .rules(List.of())
                        .build())
                .inputPorts(List.of(NodePortMetaDTO.builder()
                        .name("dataset")
                        .label("数据集")
                        .valueType(ValueType.DATASET)
                        .required(true)
                        .multiple(false)
                        .build()))
                .outputPorts(List.of(NodePortMetaDTO.builder()
                        .name("table")
                        .label("表格输出")
                        .valueType(ValueType.TABLE)
                        .required(true)
                        .multiple(false)
                        .build()))
                .capabilities(List.of(
                        NodeCapabilityDTO.builder().code("FIELD_MAPPING_TARGET").name("支持字段映射目标").build(),
                        NodeCapabilityDTO.builder().code("TABLE_OUTPUT_TARGET").name("支持表格输出").build(),
                        NodeCapabilityDTO.builder().code("PREVIEW_SUPPORTED").name("支持预览").build()))
                .build();
    }

    private NodeMetaDTO buildAggregateDefinition() {
        return NodeMetaDTO.builder()
                .protocolVersion("1.0")
                .metadataVersion("2026-04-29")
                .nodeType(NodeType.AGGREGATE.getCode())
                .nodeVersion("1.0")
                .displayName("Aggregate")
                .category(NodeCategory.COMPUTE)
                .description("聚合计算节点")
                .tags(List.of("aggregate", "compute"))
                .defaults(Map.of())
                .configSchema(NodeConfigSchemaDTO.builder()
                        .schemaType("panel")
                        .schemaVersion("1.0")
                        .panelId("analysis.aggregate")
                        .layout(Map.of("type", "section-list"))
                        .sections(List.of(
                                PanelSectionDTO.builder()
                                        .key("groupBy")
                                        .title("分组字段")
                                        .order(1)
                                        .fields(List.of(
                                                PanelFieldDTO.builder()
                                                        .field("groupByFields")
                                                        .label("GROUP BY 字段")
                                                        .componentType(FieldComponentType.FIELD_MULTI_SELECTOR)
                                                        .required(false)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(1)
                                                        .valueType(ValueType.ARRAY_STRING)
                                                        .placeholder("选择分组字段（可多选）")
                                                        .build()))
                                        .build(),
                                PanelSectionDTO.builder()
                                        .key("metrics")
                                        .title("聚合指标")
                                        .order(2)
                                        .fields(List.of(
                                                PanelFieldDTO.builder()
                                                        .field("metricField")
                                                        .label("聚合字段")
                                                        .componentType(FieldComponentType.FIELD_PICKER)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(1)
                                                        .valueType(ValueType.STRING)
                                                        .placeholder("选择聚合字段")
                                                        .validations(List.of(
                                                                ValidationRuleDTO.builder().type("required").message("请选择聚合字段").build()))
                                                        .build(),
                                                PanelFieldDTO.builder()
                                                        .field("aggregateFunc")
                                                        .label("聚合函数")
                                                        .componentType(FieldComponentType.SELECT)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(2)
                                                        .valueType(ValueType.STRING)
                                                        .defaultValue("SUM")
                                                        .options(List.of(
                                                                OptionDTO.builder().value("SUM").label("SUM（求和）").build(),
                                                                OptionDTO.builder().value("COUNT").label("COUNT（计数）").build(),
                                                                OptionDTO.builder().value("AVG").label("AVG（平均值）").build(),
                                                                OptionDTO.builder().value("MAX").label("MAX（最大值）").build(),
                                                                OptionDTO.builder().value("MIN").label("MIN（最小值）").build()))
                                                        .validations(List.of(
                                                                ValidationRuleDTO.builder().type("required").message("请选择聚合函数").build()))
                                                        .build()))
                                        .build()))
                        .rules(List.of())
                        .build())
                .inputPorts(List.of(NodePortMetaDTO.builder().name("dataset").label("数据集").valueType(ValueType.DATASET).required(true).multiple(false).build()))
                .outputPorts(List.of(NodePortMetaDTO.builder().name("dataset").label("聚合结果").valueType(ValueType.DATASET).required(true).multiple(false).build()))
                .capabilities(List.of(NodeCapabilityDTO.builder().code("COMPUTE_AGGREGATE").name("聚合计算").build()))
                .build();
    }

    private NodeMetaDTO buildTimeSeriesComputeDefinition() {
        return NodeMetaDTO.builder()
                .protocolVersion("1.0")
                .metadataVersion("2026-04-29")
                .nodeType(NodeType.TIME_SERIES_COMPUTE.getCode())
                .nodeVersion("1.0")
                .displayName("Time Series Compute")
                .category(NodeCategory.COMPUTE)
                .description("同比环比等时序计算节点")
                .tags(List.of("time-series", "compute"))
                .defaults(Map.of("computeType", "YOY", "timeGrain", "DAY"))
                .configSchema(NodeConfigSchemaDTO.builder()
                        .schemaType("panel")
                        .schemaVersion("1.0")
                        .panelId("analysis.time-series-compute")
                        .layout(Map.of("type", "section-list"))
                        .sections(List.of(
                                PanelSectionDTO.builder()
                                        .key("basic")
                                        .title("时序配置")
                                        .order(1)
                                        .fields(List.of(
                                                PanelFieldDTO.builder()
                                                        .field("timeField")
                                                        .label("时间字段")
                                                        .componentType(FieldComponentType.FIELD_PICKER)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(1)
                                                        .valueType(ValueType.STRING)
                                                        .placeholder("选择时间维度字段")
                                                        .validations(List.of(
                                                                ValidationRuleDTO.builder().type("required").message("请选择时间字段").build()))
                                                        .build(),
                                                PanelFieldDTO.builder()
                                                        .field("metricField")
                                                        .label("指标字段")
                                                        .componentType(FieldComponentType.FIELD_PICKER)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(2)
                                                        .valueType(ValueType.STRING)
                                                        .placeholder("选择数值指标字段")
                                                        .validations(List.of(
                                                                ValidationRuleDTO.builder().type("required").message("请选择指标字段").build()))
                                                        .build(),
                                                PanelFieldDTO.builder()
                                                        .field("computeType")
                                                        .label("计算类型")
                                                        .componentType(FieldComponentType.SELECT)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(3)
                                                        .valueType(ValueType.STRING)
                                                        .defaultValue("YOY")
                                                        .options(List.of(
                                                                OptionDTO.builder().value("YOY").label("同比（Year-over-Year）").build(),
                                                                OptionDTO.builder().value("MOM").label("环比（Month-over-Month）").build(),
                                                                OptionDTO.builder().value("DOD").label("日环比（Day-over-Day）").build(),
                                                                OptionDTO.builder().value("CUMSUM").label("累计求和").build()))
                                                        .validations(List.of(
                                                                ValidationRuleDTO.builder().type("required").message("请选择计算类型").build()))
                                                        .build(),
                                                PanelFieldDTO.builder()
                                                        .field("timeGrain")
                                                        .label("时间粒度")
                                                        .componentType(FieldComponentType.SELECT)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(4)
                                                        .valueType(ValueType.STRING)
                                                        .defaultValue("DAY")
                                                        .options(List.of(
                                                                OptionDTO.builder().value("DAY").label("日").build(),
                                                                OptionDTO.builder().value("WEEK").label("周").build(),
                                                                OptionDTO.builder().value("MONTH").label("月").build(),
                                                                OptionDTO.builder().value("QUARTER").label("季度").build(),
                                                                OptionDTO.builder().value("YEAR").label("年").build()))
                                                        .validations(List.of(
                                                                ValidationRuleDTO.builder().type("required").message("请选择时间粒度").build()))
                                                        .build()))
                                        .build()))
                        .rules(List.of())
                        .build())
                .inputPorts(List.of(NodePortMetaDTO.builder().name("dataset").label("数据集").valueType(ValueType.DATASET).required(true).multiple(false).build()))
                .outputPorts(List.of(NodePortMetaDTO.builder().name("dataset").label("时序结果").valueType(ValueType.DATASET).required(true).multiple(false).build()))
                .capabilities(List.of(NodeCapabilityDTO.builder().code("COMPUTE_TIME_SERIES").name("时序计算").build()))
                .build();
    }

    private NodeMetaDTO buildPivotDefinition() {
        return NodeMetaDTO.builder()
                .protocolVersion("1.0")
                .metadataVersion("2026-04-29")
                .nodeType(NodeType.PIVOT.getCode())
                .nodeVersion("1.0")
                .displayName("Pivot")
                .category(NodeCategory.COMPUTE)
                .description("透视计算节点")
                .tags(List.of("pivot", "compute"))
                .defaults(Map.of("aggregateFunc", "SUM"))
                .configSchema(NodeConfigSchemaDTO.builder()
                        .schemaType("panel")
                        .schemaVersion("1.0")
                        .panelId("analysis.pivot")
                        .layout(Map.of("type", "section-list"))
                        .sections(List.of(
                                PanelSectionDTO.builder()
                                        .key("pivot")
                                        .title("透视配置")
                                        .order(1)
                                        .fields(List.of(
                                                PanelFieldDTO.builder()
                                                        .field("rowFields")
                                                        .label("行维度")
                                                        .componentType(FieldComponentType.FIELD_MULTI_SELECTOR)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(1)
                                                        .valueType(ValueType.ARRAY_STRING)
                                                        .placeholder("选择行维度字段")
                                                        .validations(List.of(
                                                                ValidationRuleDTO.builder().type("required").message("请选择至少一个行维度").build()))
                                                        .build(),
                                                PanelFieldDTO.builder()
                                                        .field("columnField")
                                                        .label("列维度")
                                                        .componentType(FieldComponentType.FIELD_PICKER)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(2)
                                                        .valueType(ValueType.STRING)
                                                        .placeholder("选择列维度字段（值将展开为列）")
                                                        .validations(List.of(
                                                                ValidationRuleDTO.builder().type("required").message("请选择列维度").build()))
                                                        .build(),
                                                PanelFieldDTO.builder()
                                                        .field("valueField")
                                                        .label("值字段")
                                                        .componentType(FieldComponentType.FIELD_PICKER)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(3)
                                                        .valueType(ValueType.STRING)
                                                        .placeholder("选择聚合值字段")
                                                        .validations(List.of(
                                                                ValidationRuleDTO.builder().type("required").message("请选择值字段").build()))
                                                        .build(),
                                                PanelFieldDTO.builder()
                                                        .field("aggregateFunc")
                                                        .label("聚合函数")
                                                        .componentType(FieldComponentType.SELECT)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(4)
                                                        .valueType(ValueType.STRING)
                                                        .defaultValue("SUM")
                                                        .options(List.of(
                                                                OptionDTO.builder().value("SUM").label("SUM（求和）").build(),
                                                                OptionDTO.builder().value("COUNT").label("COUNT（计数）").build(),
                                                                OptionDTO.builder().value("AVG").label("AVG（平均值）").build(),
                                                                OptionDTO.builder().value("MAX").label("MAX（最大值）").build(),
                                                                OptionDTO.builder().value("MIN").label("MIN（最小值）").build()))
                                                        .validations(List.of(
                                                                ValidationRuleDTO.builder().type("required").message("请选择聚合函数").build()))
                                                        .build()))
                                        .build()))
                        .rules(List.of())
                        .build())
                .inputPorts(List.of(NodePortMetaDTO.builder().name("dataset").label("数据集").valueType(ValueType.DATASET).required(true).multiple(false).build()))
                .outputPorts(List.of(NodePortMetaDTO.builder().name("dataset").label("透视结果").valueType(ValueType.DATASET).required(true).multiple(false).build()))
                .capabilities(List.of(NodeCapabilityDTO.builder().code("COMPUTE_PIVOT").name("透视计算").build()))
                .build();
    }

    private NodeMetaDTO buildFilterDefinition() {
        return NodeMetaDTO.builder()
                .protocolVersion("1.0")
                .metadataVersion("2026-04-29")
                .nodeType(NodeType.FILTER.getCode())
                .nodeVersion("1.0")
                .displayName("Filter")
                .category(NodeCategory.COMPUTE)
                .description("过滤节点")
                .tags(List.of("filter", "compute"))
                .defaults(Map.of("operator", "EQ"))
                .configSchema(NodeConfigSchemaDTO.builder()
                        .schemaType("panel")
                        .schemaVersion("1.0")
                        .panelId("analysis.filter")
                        .layout(Map.of("type", "section-list"))
                        .sections(List.of(
                                PanelSectionDTO.builder()
                                        .key("filter")
                                        .title("过滤条件")
                                        .order(1)
                                        .fields(List.of(
                                                PanelFieldDTO.builder()
                                                        .field("filterField")
                                                        .label("过滤字段")
                                                        .componentType(FieldComponentType.FIELD_PICKER)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(1)
                                                        .valueType(ValueType.STRING)
                                                        .placeholder("选择用于过滤的字段")
                                                        .validations(List.of(
                                                                ValidationRuleDTO.builder().type("required").message("请选择过滤字段").build()))
                                                        .build(),
                                                PanelFieldDTO.builder()
                                                        .field("operator")
                                                        .label("运算符")
                                                        .componentType(FieldComponentType.SELECT)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(2)
                                                        .valueType(ValueType.STRING)
                                                        .defaultValue("EQ")
                                                        .options(List.of(
                                                                OptionDTO.builder().value(ConditionOperator.EQ.name()).label("等于").build(),
                                                                OptionDTO.builder().value(ConditionOperator.NEQ.name()).label("不等于").build(),
                                                                OptionDTO.builder().value(ConditionOperator.GT.name()).label("大于").build(),
                                                                OptionDTO.builder().value(ConditionOperator.GTE.name()).label("大于等于").build(),
                                                                OptionDTO.builder().value(ConditionOperator.LT.name()).label("小于").build(),
                                                                OptionDTO.builder().value(ConditionOperator.LTE.name()).label("小于等于").build(),
                                                                OptionDTO.builder().value(ConditionOperator.CONTAINS.name()).label("包含").build(),
                                                                OptionDTO.builder().value(ConditionOperator.NOT_CONTAINS.name()).label("不包含").build(),
                                                                OptionDTO.builder().value(ConditionOperator.IS_EMPTY.name()).label("为空").build(),
                                                                OptionDTO.builder().value(ConditionOperator.IS_NOT_EMPTY.name()).label("不为空").build()))
                                                        .validations(List.of(
                                                                ValidationRuleDTO.builder().type("required").message("请选择运算符").build()))
                                                        .build(),
                                                PanelFieldDTO.builder()
                                                        .field("filterValue")
                                                        .label("过滤值")
                                                        .componentType(FieldComponentType.INPUT)
                                                        .required(false)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(3)
                                                        .valueType(ValueType.STRING)
                                                        .placeholder("IS_EMPTY / IS_NOT_EMPTY 无需填写")
                                                        .build()))
                                        .build()))
                        .rules(List.of())
                        .build())
                .inputPorts(List.of(NodePortMetaDTO.builder().name("dataset").label("数据集").valueType(ValueType.DATASET).required(true).multiple(false).build()))
                .outputPorts(List.of(NodePortMetaDTO.builder().name("dataset").label("过滤结果").valueType(ValueType.DATASET).required(true).multiple(false).build()))
                .capabilities(List.of(NodeCapabilityDTO.builder().code("COMPUTE_FILTER").name("过滤计算").build()))
                .build();
    }

    private NodeMetaDTO buildSortDefinition() {
        return NodeMetaDTO.builder()
                .protocolVersion("1.0")
                .metadataVersion("2026-04-29")
                .nodeType(NodeType.SORT.getCode())
                .nodeVersion("1.0")
                .displayName("Sort")
                .category(NodeCategory.COMPUTE)
                .description("排序节点")
                .tags(List.of("sort", "compute"))
                .defaults(Map.of("sortOrder", "ASC", "limit", 0))
                .configSchema(NodeConfigSchemaDTO.builder()
                        .schemaType("panel")
                        .schemaVersion("1.0")
                        .panelId("analysis.sort")
                        .layout(Map.of("type", "section-list"))
                        .sections(List.of(
                                PanelSectionDTO.builder()
                                        .key("sort")
                                        .title("排序配置")
                                        .order(1)
                                        .fields(List.of(
                                                PanelFieldDTO.builder()
                                                        .field("sortField")
                                                        .label("排序字段")
                                                        .componentType(FieldComponentType.FIELD_PICKER)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(1)
                                                        .valueType(ValueType.STRING)
                                                        .placeholder("选择排序字段")
                                                        .validations(List.of(
                                                                ValidationRuleDTO.builder().type("required").message("请选择排序字段").build()))
                                                        .build(),
                                                PanelFieldDTO.builder()
                                                        .field("sortOrder")
                                                        .label("排序方向")
                                                        .componentType(FieldComponentType.SELECT)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(2)
                                                        .valueType(ValueType.STRING)
                                                        .defaultValue("ASC")
                                                        .options(List.of(
                                                                OptionDTO.builder().value("ASC").label("升序（ASC）").build(),
                                                                OptionDTO.builder().value("DESC").label("降序（DESC）").build()))
                                                        .validations(List.of(
                                                                ValidationRuleDTO.builder().type("required").message("请选择排序方向").build()))
                                                        .build(),
                                                PanelFieldDTO.builder()
                                                        .field("limit")
                                                        .label("取 TOP N（0 表示不限制）")
                                                        .componentType(FieldComponentType.NUMBER_INPUT)
                                                        .required(false)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(3)
                                                        .valueType(ValueType.INTEGER)
                                                        .defaultValue(0)
                                                        .validations(List.of(
                                                                ValidationRuleDTO.builder().type("min").min(0).message("不能为负数").build()))
                                                        .build()))
                                        .build()))
                        .rules(List.of())
                        .build())
                .inputPorts(List.of(NodePortMetaDTO.builder().name("dataset").label("数据集").valueType(ValueType.DATASET).required(true).multiple(false).build()))
                .outputPorts(List.of(NodePortMetaDTO.builder().name("dataset").label("排序结果").valueType(ValueType.DATASET).required(true).multiple(false).build()))
                .capabilities(List.of(NodeCapabilityDTO.builder().code("COMPUTE_SORT").name("排序计算").build()))
                .build();
    }

    private NodeMetaDTO buildFormulaDefinition() {
        return NodeMetaDTO.builder()
                .protocolVersion("1.0")
                .metadataVersion("2026-04-29")
                .nodeType(NodeType.FORMULA.getCode())
                .nodeVersion("1.0")
                .displayName("Formula")
                .category(NodeCategory.COMPUTE)
                .description("派生指标节点")
                .tags(List.of("formula", "compute"))
                .defaults(Map.of())
                .configSchema(NodeConfigSchemaDTO.builder()
                        .schemaType("panel")
                        .schemaVersion("1.0")
                        .panelId("analysis.formula")
                        .layout(Map.of("type", "section-list"))
                        .sections(List.of(
                                PanelSectionDTO.builder()
                                        .key("formula")
                                        .title("派生字段")
                                        .order(1)
                                        .fields(List.of(
                                                PanelFieldDTO.builder()
                                                        .field("outputField")
                                                        .label("新字段名称")
                                                        .componentType(FieldComponentType.INPUT)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(1)
                                                        .valueType(ValueType.STRING)
                                                        .placeholder("如 profit_margin")
                                                        .validations(List.of(
                                                                ValidationRuleDTO.builder().type("required").message("请填写新字段名称").build(),
                                                                ValidationRuleDTO.builder().type("maxLength").maxLength(64).message("字段名最长 64 字符").build()))
                                                        .build(),
                                                PanelFieldDTO.builder()
                                                        .field("expression")
                                                        .label("计算表达式")
                                                        .componentType(FieldComponentType.TEXTAREA)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(2)
                                                        .valueType(ValueType.STRING)
                                                        .placeholder("如 sales_amount / order_count")
                                                        .description("支持四则运算及字段引用，字段名直接输入即可")
                                                        .validations(List.of(
                                                                ValidationRuleDTO.builder().type("required").message("请填写计算表达式").build()))
                                                        .build()))
                                        .build()))
                        .rules(List.of())
                        .build())
                .inputPorts(List.of(NodePortMetaDTO.builder().name("dataset").label("数据集").valueType(ValueType.DATASET).required(true).multiple(false).build()))
                .outputPorts(List.of(NodePortMetaDTO.builder().name("dataset").label("派生结果").valueType(ValueType.DATASET).required(true).multiple(false).build()))
                .capabilities(List.of(NodeCapabilityDTO.builder().code("COMPUTE_FORMULA").name("公式计算").build()))
                .build();
    }

    private NodeMetaDTO buildPythonScriptDefinition() {
        return NodeMetaDTO.builder()
                .protocolVersion("1.0")
                .metadataVersion("2026-05-08")
                .nodeType(NodeType.PYTHON_SCRIPT.getCode())
                .nodeVersion("1.0")
                .displayName("Python Script")
                .category(NodeCategory.COMPUTE)
                .description("自定义 Python 3 脚本处理数据")
                .tags(List.of("python", "script", "compute", "custom"))
                .defaults(Map.of("timeoutSeconds", 30))
                .configSchema(NodeConfigSchemaDTO.builder()
                        .schemaType("panel")
                        .schemaVersion("1.0")
                        .panelId("analysis.python-script")
                        .layout(Map.of("type", "section-list"))
                        .sections(List.of(
                                PanelSectionDTO.builder()
                                        .key("script")
                                        .title("脚本")
                                        .order(1)
                                        .fields(List.of(
                                                PanelFieldDTO.builder()
                                                        .field("script")
                                                        .label("Python 代码")
                                                        .componentType(FieldComponentType.CODE_EDITOR)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(1)
                                                        .valueType(ValueType.STRING)
                                                        .placeholder("# rows: List[dict] — 上游数据\n# 将结果赋值给 output_rows\noutput_rows = rows")
                                                        .props(Map.of("language", "python", "minLines", 10))
                                                        .validations(List.of(
                                                                ValidationRuleDTO.builder().type("required").message("Python 脚本不能为空").build()))
                                                        .build()))
                                        .build(),
                                PanelSectionDTO.builder()
                                        .key("options")
                                        .title("执行设置")
                                        .order(2)
                                        .fields(List.of(
                                                PanelFieldDTO.builder()
                                                        .field("timeoutSeconds")
                                                        .label("超时(秒)")
                                                        .componentType(FieldComponentType.NUMBER_INPUT)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(1)
                                                        .valueType(ValueType.INTEGER)
                                                        .defaultValue(30)
                                                        .validations(List.of(
                                                                ValidationRuleDTO.builder().type("min").min(1).message("最小超时 1 秒").build(),
                                                                ValidationRuleDTO.builder().type("max").max(300).message("最大超时 300 秒").build()))
                                                        .build()))
                                        .build()))
                        .rules(List.of())
                        .build())
                .inputPorts(List.of(NodePortMetaDTO.builder()
                        .name("dataset")
                        .label("输入数据集")
                        .valueType(ValueType.DATASET)
                        .required(true)
                        .multiple(false)
                        .build()))
                .outputPorts(List.of(NodePortMetaDTO.builder()
                        .name("dataset")
                        .label("处理结果")
                        .valueType(ValueType.DATASET)
                        .required(true)
                        .multiple(false)
                        .build()))
                .capabilities(List.of(
                        NodeCapabilityDTO.builder().code("COMPUTE_CUSTOM").name("自定义计算").build()))
                .build();
    }

    private NodeMetaDTO buildJavaCodeDefinition() {
        return NodeMetaDTO.builder()
                .protocolVersion("1.0")
                .metadataVersion("2026-05-08")
                .nodeType(NodeType.JAVA_CODE.getCode())
                .nodeVersion("1.0")
                .displayName("Java Code")
                .category(NodeCategory.COMPUTE)
                .description("自定义 Java 代码处理数据")
                .tags(List.of("java", "code", "compute", "custom"))
                .defaults(Map.of("timeoutSeconds", 30))
                .configSchema(NodeConfigSchemaDTO.builder()
                        .schemaType("panel")
                        .schemaVersion("1.0")
                        .panelId("analysis.java-code")
                        .layout(Map.of("type", "section-list"))
                        .sections(List.of(
                                PanelSectionDTO.builder()
                                        .key("code")
                                        .title("代码")
                                        .order(1)
                                        .fields(List.of(
                                                PanelFieldDTO.builder()
                                                        .field("code")
                                                        .label("Java 代码")
                                                        .componentType(FieldComponentType.CODE_EDITOR)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(1)
                                                        .valueType(ValueType.STRING)
                                                        .placeholder("// rows: List<Map<String, Object>> — 上游数据\n// 方法签名: List<Map<String, Object>> process(List<Map<String, Object>> rows)\nreturn rows;")
                                                        .props(Map.of("language", "java", "minLines", 10))
                                                        .validations(List.of(
                                                                ValidationRuleDTO.builder().type("required").message("Java 代码不能为空").build()))
                                                        .build()))
                                        .build(),
                                PanelSectionDTO.builder()
                                        .key("options")
                                        .title("执行设置")
                                        .order(2)
                                        .fields(List.of(
                                                PanelFieldDTO.builder()
                                                        .field("timeoutSeconds")
                                                        .label("超时(秒)")
                                                        .componentType(FieldComponentType.NUMBER_INPUT)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(1)
                                                        .valueType(ValueType.INTEGER)
                                                        .defaultValue(30)
                                                        .validations(List.of(
                                                                ValidationRuleDTO.builder().type("min").min(1).message("最小超时 1 秒").build(),
                                                                ValidationRuleDTO.builder().type("max").max(300).message("最大超时 300 秒").build()))
                                                        .build()))
                                        .build()))
                        .rules(List.of())
                        .build())
                .inputPorts(List.of(NodePortMetaDTO.builder()
                        .name("dataset")
                        .label("输入数据集")
                        .valueType(ValueType.DATASET)
                        .required(true)
                        .multiple(false)
                        .build()))
                .outputPorts(List.of(NodePortMetaDTO.builder()
                        .name("dataset")
                        .label("处理结果")
                        .valueType(ValueType.DATASET)
                        .required(true)
                        .multiple(false)
                        .build()))
                .capabilities(List.of(
                        NodeCapabilityDTO.builder().code("COMPUTE_CUSTOM").name("自定义计算").build()))
                .build();
    }

    private NodeMetaDTO buildConditionDefinition() {
        return NodeMetaDTO.builder()
                .protocolVersion("1.0")
                .metadataVersion("2026-05-11")
                .nodeType(NodeType.CONDITION.getCode())
                .nodeVersion("1.0")
                .displayName("条件分支")
                .category(NodeCategory.GOVERNANCE)
                .description("根据上游数据字段值进行条件判断，激活 true 或 false 出边")
                .tags(List.of("condition", "branch", "if-else", "governance"))
                .defaults(Map.of())
                .configSchema(NodeConfigSchemaDTO.builder()
                        .schemaType("panel")
                        .schemaVersion("1.0")
                        .panelId("analysis.condition")
                        .layout(Map.of("type", "section-list"))
                        .sections(List.of(
                                PanelSectionDTO.builder()
                                        .key("condition")
                                        .title("条件配置")
                                        .order(1)
                                        .fields(List.of(
                                                PanelFieldDTO.builder()
                                                        .field("sourceNodeId")
                                                        .label("引用上游节点")
                                                        .componentType(FieldComponentType.VARIABLE_PICKER)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(1)
                                                        .valueType(ValueType.STRING)
                                                        .placeholder("选择上游节点")
                                                        .validations(List.of(
                                                                ValidationRuleDTO.builder().type("required").message("请选择上游节点").build()))
                                                        .build(),
                                                PanelFieldDTO.builder()
                                                        .field("fieldPath")
                                                        .label("字段路径")
                                                        .componentType(FieldComponentType.INPUT)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(2)
                                                        .valueType(ValueType.STRING)
                                                        .placeholder("如 dataset.rows.0.amount")
                                                        .validations(List.of(
                                                                ValidationRuleDTO.builder().type("required").message("请填写字段路径").build()))
                                                        .build(),
                                                PanelFieldDTO.builder()
                                                        .field("operator")
                                                        .label("运算符")
                                                        .componentType(FieldComponentType.SELECT)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(3)
                                                        .valueType(ValueType.STRING)
                                                        .optionsSource(OptionsSourceDTO.builder()
                                                                .type("static")
                                                                .options(List.of(
                                                                        OptionDTO.builder().value(ConditionOperator.EQ.name()).label("等于 ").build(),
                                                                        OptionDTO.builder().value(ConditionOperator.NEQ.name()).label("不等于").build(),
                                                                        OptionDTO.builder().value(ConditionOperator.GT.name()).label("大于").build(),
                                                                        OptionDTO.builder().value(ConditionOperator.GTE.name()).label("大于等于").build(),
                                                                        OptionDTO.builder().value(ConditionOperator.LT.name()).label("小于").build(),
                                                                        OptionDTO.builder().value(ConditionOperator.LTE.name()).label("小于等于").build(),
                                                                        OptionDTO.builder().value(ConditionOperator.CONTAINS.name()).label("包含").build(),
                                                                        OptionDTO.builder().value(ConditionOperator.NOT_CONTAINS.name()).label("不包含").build(),
                                                                        OptionDTO.builder().value(ConditionOperator.IN.name()).label("在列表中 (IN)").build(),
                                                                        OptionDTO.builder().value(ConditionOperator.NOT_IN.name()).label("不在列表中 (NOT IN)").build(),
                                                                        OptionDTO.builder().value(ConditionOperator.IS_EMPTY.name()).label("为空").build(),
                                                                        OptionDTO.builder().value(ConditionOperator.IS_NOT_EMPTY.name()).label("不为空").build()))
                                                                .build())
                                                        .validations(List.of(
                                                                ValidationRuleDTO.builder().type("required").message("请选择运算符").build()))
                                                        .build(),
                                                PanelFieldDTO.builder()
                                                        .field("compareValue")
                                                        .label("比较值")
                                                        .componentType(FieldComponentType.INPUT)
                                                        .required(false)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(4)
                                                        .valueType(ValueType.STRING)
                                                        .placeholder("IS_EMPTY/IS_NOT_EMPTY 无需填写")
                                                        .build()))
                                        .build()))
                        .rules(List.of())
                        .build())
                .inputPorts(List.of(NodePortMetaDTO.builder()
                        .name("input")
                        .label("上游结果")
                        .valueType(ValueType.ANY)
                        .required(true)
                        .multiple(false)
                        .build()))
                .outputPorts(List.of(
                        NodePortMetaDTO.builder()
                                .name("true")
                                .label("条件成立")
                                .valueType(ValueType.ANY)
                                .required(false)
                                .multiple(false)
                                .build(),
                        NodePortMetaDTO.builder()
                                .name("false")
                                .label("条件不成立")
                                .valueType(ValueType.ANY)
                                .required(false)
                                .multiple(false)
                                .build()))
                .capabilities(List.of(
                        NodeCapabilityDTO.builder().code("CONDITIONAL_ROUTING").name("条件路由").build()))
                .build();
    }

    private NodeMetaDTO buildErrorHandlerDefinition() {
        return NodeMetaDTO.builder()
                .protocolVersion("1.0")
                .metadataVersion("2026-05-11")
                .nodeType(NodeType.ERROR_HANDLER.getCode())
                .nodeVersion("1.0")
                .displayName("错误恢复")
                .category(NodeCategory.GOVERNANCE)
                .description("监听指定节点的失败状态，执行重试或 fallback 兜底，避免整体工作流中断")
                .tags(List.of("error", "handler", "retry", "fallback", "governance"))
                .defaults(Map.of("maxRetries", 0, "retryDelayMs", 1000, "fallbackBehavior", "SKIP"))
                .configSchema(NodeConfigSchemaDTO.builder()
                        .schemaType("panel")
                        .schemaVersion("1.0")
                        .panelId("analysis.error-handler")
                        .layout(Map.of("type", "section-list"))
                        .sections(List.of(
                                PanelSectionDTO.builder()
                                        .key("target")
                                        .title("保护目标")
                                        .order(1)
                                        .fields(List.of(
                                                PanelFieldDTO.builder()
                                                        .field("guardedNodeId")
                                                        .label("被保护节点")
                                                        .componentType(FieldComponentType.VARIABLE_PICKER)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(1)
                                                        .valueType(ValueType.STRING)
                                                        .placeholder("选择要保护的节点")
                                                        .validations(List.of(
                                                                ValidationRuleDTO.builder().type("required").message("请选择被保护节点").build()))
                                                        .build()))
                                        .build(),
                                PanelSectionDTO.builder()
                                        .key("retry")
                                        .title("重试策略")
                                        .order(2)
                                        .fields(List.of(
                                                PanelFieldDTO.builder()
                                                        .field("maxRetries")
                                                        .label("最大重试次数")
                                                        .componentType(FieldComponentType.NUMBER_INPUT)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(1)
                                                        .valueType(ValueType.INTEGER)
                                                        .defaultValue(0)
                                                        .validations(List.of(
                                                                ValidationRuleDTO.builder().type("min").min(0).message("重试次数最小为 0").build(),
                                                                ValidationRuleDTO.builder().type("max").max(5).message("重试次数最大为 5").build()))
                                                        .build(),
                                                PanelFieldDTO.builder()
                                                        .field("retryDelayMs")
                                                        .label("重试间隔(ms)")
                                                        .componentType(FieldComponentType.NUMBER_INPUT)
                                                        .required(false)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(2)
                                                        .valueType(ValueType.INTEGER)
                                                        .defaultValue(1000)
                                                        .build()))
                                        .build(),
                                PanelSectionDTO.builder()
                                        .key("fallback")
                                        .title("兜底行为")
                                        .order(3)
                                        .fields(List.of(
                                                PanelFieldDTO.builder()
                                                        .field("fallbackBehavior")
                                                        .label("失败处理方式")
                                                        .componentType(FieldComponentType.SELECT)
                                                        .required(true)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(1)
                                                        .valueType(ValueType.STRING)
                                                        .defaultValue("SKIP")
                                                        .optionsSource(OptionsSourceDTO.builder()
                                                                .type("static")
                                                                .options(List.of(
                                                                        OptionDTO.builder().value("SKIP").label("跳过（整体继续）").build(),
                                                                        OptionDTO.builder().value("DEFAULT_VALUE").label("使用默认值").build(),
                                                                        OptionDTO.builder().value("FAIL").label("向下传播失败").build()))
                                                                .build())
                                                        .build(),
                                                PanelFieldDTO.builder()
                                                        .field("defaultValue")
                                                        .label("默认值")
                                                        .componentType(FieldComponentType.INPUT)
                                                        .required(false)
                                                        .visible(true)
                                                        .editable(true)
                                                        .order(2)
                                                        .valueType(ValueType.STRING)
                                                        .placeholder("fallbackBehavior=DEFAULT_VALUE 时使用")
                                                        .build()))
                                        .build()))
                        .rules(List.of())
                        .build())
                .inputPorts(List.of(NodePortMetaDTO.builder()
                        .name("input")
                        .label("被保护节点输出")
                        .valueType(ValueType.ANY)
                        .required(true)
                        .multiple(false)
                        .build()))
                .outputPorts(List.of(NodePortMetaDTO.builder()
                        .name("output")
                        .label("恢复结果")
                        .valueType(ValueType.ANY)
                        .required(false)
                        .multiple(false)
                        .build()))
                .capabilities(List.of(
                        NodeCapabilityDTO.builder().code("ERROR_RECOVERY").name("错误恢复").build()))
                .build();
    }

    private FieldCandidateSlotDTO buildSlot(String slot, Boolean required, List<String> acceptedTypes,
                                            List<String> acceptedCapabilities,
                                            List<FieldMappingCandidateDTO> candidates) {
        return FieldCandidateSlotDTO.builder()
                .slot(slot)
                .required(required)
                .acceptedTypes(acceptedTypes)
                .acceptedCapabilities(acceptedCapabilities)
                .candidates(candidates)
                .build();
    }

    private FieldMappingCandidateDTO candidate(String fieldId, double score, String reason) {
        return FieldMappingCandidateDTO.builder()
                .fieldId(fieldId)
                .score(score)
                .reason(reason)
                .build();
    }

    private List<FieldCandidateSlotDTO> buildComputeNodeCandidates(String nodeType, List<FieldSchemaDTO> upstreamFields) {
        List<FieldMappingCandidateDTO> allCandidates = upstreamFields.stream()
                .map(f -> candidate(f.getName(), 1.0, "upstream field"))
                .toList();
        List<FieldMappingCandidateDTO> metricCandidates = upstreamFields.stream()
                .filter(f -> f.getSemanticType() == FieldSemanticType.METRIC
                        || f.getValueType() == ValueType.INTEGER
                        || f.getValueType() == ValueType.LONG
                        || f.getValueType() == ValueType.DECIMAL)
                .map(f -> candidate(f.getName(), 1.0, "metric field"))
                .toList();
        List<FieldMappingCandidateDTO> timeCandidates = upstreamFields.stream()
                .filter(f -> f.getSemanticType() == FieldSemanticType.TIME_DIMENSION
                        || f.getValueType() == ValueType.DATE
                        || f.getValueType() == ValueType.DATETIME)
                .map(f -> candidate(f.getName(), 1.0, "time field"))
                .toList();
        if (!metricCandidates.isEmpty() && timeCandidates.isEmpty()) {
            timeCandidates = allCandidates;
        }
        if (!timeCandidates.isEmpty() && metricCandidates.isEmpty()) {
            metricCandidates = allCandidates;
        }

        if (NodeType.AGGREGATE.getCode().equals(nodeType)) {
            return List.of(
                    buildSlot("groupByFields", false, List.of("ANY"), List.of("GROUPABLE"), allCandidates),
                    buildSlot("metricField", true, List.of("ANY"), List.of("AGGREGATABLE"), metricCandidates.isEmpty() ? allCandidates : metricCandidates));
        }
        if (NodeType.FILTER.getCode().equals(nodeType)) {
            return List.of(buildSlot("filterField", true, List.of("ANY"), List.of("SELECTABLE"), allCandidates));
        }
        if (NodeType.SORT.getCode().equals(nodeType)) {
            return List.of(buildSlot("sortField", true, List.of("ANY"), List.of("SELECTABLE"), allCandidates));
        }
        if (NodeType.FORMULA.getCode().equals(nodeType)) {
            return List.of();
        }
        if (NodeType.PIVOT.getCode().equals(nodeType)) {
            return List.of(
                    buildSlot("rowFields", true, List.of("ANY"), List.of("GROUPABLE"), allCandidates),
                    buildSlot("columnField", true, List.of("ANY"), List.of("GROUPABLE"), allCandidates),
                    buildSlot("valueField", true, List.of("ANY"), List.of("AGGREGATABLE"), metricCandidates.isEmpty() ? allCandidates : metricCandidates));
        }
        if (NodeType.TIME_SERIES_COMPUTE.getCode().equals(nodeType)) {
            return List.of(
                    buildSlot("timeField", true, List.of("ANY"), List.of("X_AXIS_CANDIDATE"), timeCandidates.isEmpty() ? allCandidates : timeCandidates),
                    buildSlot("metricField", true, List.of("ANY"), List.of("AGGREGATABLE"), metricCandidates.isEmpty() ? allCandidates : metricCandidates));
        }
        return List.of();
    }

    private NodeMetaDTO buildDataJoinDefinition() {
        return NodeMetaDTO.builder()
                .protocolVersion("1.0")
                .metadataVersion("2026-05-11")
                .nodeType(NodeType.DATA_JOIN.getCode())
                .nodeVersion("1.0")
                .displayName("Data Join")
                .category(NodeCategory.COMPUTE)
                .description("跨数据源 JOIN 节点，支持 INNER/LEFT/RIGHT/FULL JOIN；同源数据集自动下推 SQL JOIN")
                .tags(List.of("join", "compute", "cross-source"))
                .inputPorts(List.of(
                        NodePortMetaDTO.builder()
                                .name("leftDataset")
                                .label("左表")
                                .valueType(ValueType.DATASET)
                                .required(true)
                                .multiple(false)
                                .build(),
                        NodePortMetaDTO.builder()
                                .name("rightDataset")
                                .label("右表")
                                .valueType(ValueType.DATASET)
                                .required(true)
                                .multiple(false)
                                .build()))
                .outputPorts(List.of(NodePortMetaDTO.builder()
                        .name("dataset")
                        .label("JOIN 结果")
                        .valueType(ValueType.DATASET)
                        .required(true)
                        .multiple(false)
                        .build()))
                .capabilities(List.of(NodeCapabilityDTO.builder()
                        .code("COMPUTE_DATA_JOIN")
                        .name("跨源 JOIN")
                        .build()))
                .build();
    }
}
