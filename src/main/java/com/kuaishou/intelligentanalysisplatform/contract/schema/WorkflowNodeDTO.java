package com.kuaishou.intelligentanalysisplatform.contract.schema;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.kuaishou.intelligentanalysisplatform.contract.enums.NodeCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowNodeDTO {
    private String nodeId;
    private String nodeType;
    private NodeCategory category;
    private String version;
    private NodeMetaDTO metadata;

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
            property = "nodeType",
            visible = true,
            defaultImpl = RawNodeConfigDTO.class)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = SqlQueryNodeConfigDTO.class, name = "sql_query"),
            @JsonSubTypes.Type(value = AggregateNodeConfigDTO.class, name = "aggregate"),
            @JsonSubTypes.Type(value = TimeSeriesComputeNodeConfigDTO.class, name = "time_series_compute"),
            @JsonSubTypes.Type(value = PivotNodeConfigDTO.class, name = "pivot"),
            @JsonSubTypes.Type(value = FilterNodeConfigDTO.class, name = "filter"),
            @JsonSubTypes.Type(value = SortNodeConfigDTO.class, name = "sort"),
            @JsonSubTypes.Type(value = FormulaNodeConfigDTO.class, name = "formula"),
            @JsonSubTypes.Type(value = ChartOutputNodeConfigDTO.class, name = "chart_output"),
            @JsonSubTypes.Type(value = TableOutputNodeConfigDTO.class, name = "table_output")
    })
    private BaseNodeConfigDTO config;
}
