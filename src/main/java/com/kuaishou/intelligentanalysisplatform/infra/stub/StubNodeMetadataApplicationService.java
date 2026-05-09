package com.kuaishou.intelligentanalysisplatform.infra.stub;

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
public class StubNodeMetadataApplicationService implements NodeMetadataApplicationService {

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
                buildTableOutputDefinition());
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
        if (NodeType.CHART_OUTPUT.getCode().equals(nodeType)) {
            return getChartMappingCandidates(renderer);
        }
        if (NodeType.TABLE_OUTPUT.getCode().equals(nodeType)) {
            if (renderer != null && !renderer.isBlank() && !"table".equalsIgnoreCase(renderer)) {
                throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "renderer is not supported for table_output");
            }
            return List.of(buildSlot("columns", true, List.of("ANY"),
                    List.of("TABLE_COLUMN_CANDIDATE"),
                    List.of(
                            candidate("order_date", 0.99, "table default column"),
                            candidate("product_name", 0.98, "table default column"),
                            candidate("sales_amount", 0.97, "table default column"),
                            candidate("order_count", 0.95, "table default column"))));
        }
        throw new BaseBusinessException(ErrorCode.NODE_NOT_FOUND, "mapping candidates not found");
    }

    private List<FieldCandidateSlotDTO> getChartMappingCandidates(String renderer) {
        if (renderer == null || renderer.isBlank()) {
            throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "renderer is required for chart_output");
        }
        String normalizedRenderer = renderer.toLowerCase();
        switch (normalizedRenderer) {
            case "line":
            case "bar":
            case "area":
                return List.of(
                        buildSlot("xField", true, List.of("DATE", "DATETIME", "STRING"),
                                List.of("X_AXIS_CANDIDATE"),
                                List.of(candidate("order_date", 0.98, "semanticType=time_dimension"))),
                        buildSlot("yField", true, List.of("DECIMAL", "INTEGER", "LONG"),
                                List.of("Y_AXIS_CANDIDATE", "AGGREGATABLE"),
                                List.of(
                                        candidate("sales_amount", 0.97, "semanticType=metric"),
                                        candidate("order_count", 0.91, "semanticType=metric"))),
                        buildSlot("seriesField", false, List.of("STRING"),
                                List.of("SERIES_CANDIDATE"),
                                List.of(candidate("product_name", 0.90, "semanticType=dimension"))));
            case "scatter":
                return List.of(
                        buildSlot("xField", true, List.of("DECIMAL", "INTEGER", "LONG"),
                                List.of("X_AXIS_CANDIDATE", "AGGREGATABLE"),
                                List.of(candidate("order_count", 0.93, "numeric scatter axis"))),
                        buildSlot("yField", true, List.of("DECIMAL", "INTEGER", "LONG"),
                                List.of("Y_AXIS_CANDIDATE", "AGGREGATABLE"),
                                List.of(candidate("sales_amount", 0.97, "numeric scatter axis"))),
                        buildSlot("seriesField", false, List.of("STRING"),
                                List.of("SERIES_CANDIDATE"),
                                List.of(candidate("product_name", 0.88, "scatter grouping"))));
            case "pie":
                return List.of(
                        buildSlot("categoryField", true, List.of("STRING", "DATE"),
                                List.of("LABEL_CANDIDATE", "GROUPABLE"),
                                List.of(candidate("product_name", 0.96, "pie label field"))),
                        buildSlot("valueField", true, List.of("DECIMAL", "INTEGER", "LONG"),
                                List.of("Y_AXIS_CANDIDATE", "AGGREGATABLE"),
                                List.of(candidate("sales_amount", 0.98, "pie value field"))));
            default:
                throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "renderer is not supported for chart_output");
        }
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
                .reasons(List.of(reason))
                .build();
    }
}
