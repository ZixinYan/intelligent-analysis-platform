package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class AggregateNodeConfigDTO extends BaseNodeConfigDTO {
    private VariableRefDTO datasetRef;
    private List<String> groupByFields;
    private List<AggregateMetricDTO> metrics;
    private List<SortFieldDTO> sortFields;
    private Integer topN;
    private Boolean pushdownEnabled;

    // ── 表单扁平字段（来自 panel 的 metricField / aggregateFunc / metricAlias） ──
    // 当 metrics 为空时，executor 会从这三个字段自动构建 metrics 列表
    private String metricField;
    private String aggregateFunc;
    private String metricAlias;
}
