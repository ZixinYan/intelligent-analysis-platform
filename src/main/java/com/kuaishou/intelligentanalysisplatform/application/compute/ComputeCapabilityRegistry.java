package com.kuaishou.intelligentanalysisplatform.application.compute;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.contract.enums.AggregateFunction;
import com.kuaishou.intelligentanalysisplatform.contract.enums.FilterOperator;
import com.kuaishou.intelligentanalysisplatform.contract.enums.TimeGranularity;
import com.kuaishou.intelligentanalysisplatform.contract.enums.TimeSeriesComputeType;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AggregateCapabilityParamsDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.CapabilityConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FilterCapabilityParamsDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FormulaCapabilityParamsDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeCapabilityDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.PivotCapabilityParamsDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SortCapabilityParamsDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.TimeSeriesCapabilityParamsDTO;
import org.springframework.stereotype.Component;

@Component
public class ComputeCapabilityRegistry {
    private final ObjectMapper objectMapper;
    private final Map<String, NodeCapabilityDTO> capabilityMap;

    public ComputeCapabilityRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.capabilityMap = buildCapabilities();
    }

    public List<NodeCapabilityDTO> listAll() {
        return List.copyOf(capabilityMap.values());
    }

    public NodeCapabilityDTO getByCode(String code) {
        return capabilityMap.get(code);
    }

    private Map<String, NodeCapabilityDTO> buildCapabilities() {
        Map<String, NodeCapabilityDTO> capabilities = new LinkedHashMap<>();
        register(capabilities, aggregateCapability());
        register(capabilities, timeSeriesCapability());
        register(capabilities, pivotCapability());
        register(capabilities, filterCapability());
        register(capabilities, sortCapability());
        register(capabilities, formulaCapability());
        return capabilities;
    }

    private void register(Map<String, NodeCapabilityDTO> capabilities, NodeCapabilityDTO capability) {
        capabilities.put(capability.getCode(), capability);
    }

    private NodeCapabilityDTO aggregateCapability() {
        return NodeCapabilityDTO.builder()
                .code("aggregate")
                .name("Aggregate Capability")
                .capabilityConfig(CapabilityConfigDTO.builder()
                        .sqlPushdownSupported(true)
                        .pushdownBoundary("GROUP_BY_AGG_WITH_FALLBACK")
                        .inputConstraints(List.of("datasetRef required", "metrics required"))
                        .outputGuarantees(List.of("aggregated dataset", "group by dimensions preserved"))
                        .extensions(Map.of("explainable", true, "auditable", true))
                        .build())
                .params(toMap(AggregateCapabilityParamsDTO.builder()
                        .supportedFunctions(List.of(valuesOf(AggregateFunction.values())))
                        .topNSupported(true)
                        .inlineSortSupported(true)
                        .build()))
                .build();
    }

    private NodeCapabilityDTO timeSeriesCapability() {
        return NodeCapabilityDTO.builder()
                .code("time_series_compute")
                .name("Time Series Capability")
                .capabilityConfig(CapabilityConfigDTO.builder()
                        .supportedComputeTypes(List.of(valuesOf(TimeSeriesComputeType.values())))
                        .supportedGranularities(List.of(valuesOf(TimeGranularity.values())))
                        .sqlPushdownSupported(false)
                        .pushdownBoundary("IN_MEMORY_PERIOD_SHIFT")
                        .inputConstraints(List.of("timeField required", "metrics required", "granularity required"))
                        .outputGuarantees(List.of("time aligned dataset", "derived comparison metrics appended"))
                        .extensions(Map.of("explainable", true, "auditable", true))
                        .build())
                .params(toMap(TimeSeriesCapabilityParamsDTO.builder()
                        .supportedComputeTypes(List.of(valuesOf(TimeSeriesComputeType.values())))
                        .supportedGranularities(List.of(valuesOf(TimeGranularity.values())))
                        .compareShiftSupported(true)
                        .movingAverageSupported(true)
                        .build()))
                .build();
    }

    private NodeCapabilityDTO pivotCapability() {
        return NodeCapabilityDTO.builder()
                .code("pivot")
                .name("Pivot Capability")
                .capabilityConfig(CapabilityConfigDTO.builder()
                        .sqlPushdownSupported(false)
                        .pushdownBoundary("IN_MEMORY_PIVOT")
                        .inputConstraints(List.of("columnField required", "valueField required"))
                        .outputGuarantees(List.of("pivoted dataset", "row fields preserved"))
                        .extensions(Map.of("explainable", true, "auditable", true))
                        .build())
                .params(toMap(PivotCapabilityParamsDTO.builder()
                        .rowFieldRequired(false)
                        .columnFieldRequired(true)
                        .valueFieldRequired(true)
                        .aggregateFieldSupported(true)
                        .build()))
                .build();
    }

    private NodeCapabilityDTO filterCapability() {
        return NodeCapabilityDTO.builder()
                .code("filter")
                .name("Filter Capability")
                .capabilityConfig(CapabilityConfigDTO.builder()
                        .sqlPushdownSupported(true)
                        .pushdownBoundary("WHERE_PUSHDOWN_WITH_FALLBACK")
                        .inputConstraints(List.of("datasetRef required"))
                        .outputGuarantees(List.of("filtered dataset", "row order preserved before downstream sort"))
                        .extensions(Map.of("explainable", true, "auditable", true))
                        .build())
                .params(toMap(FilterCapabilityParamsDTO.builder()
                        .supportedOperators(List.of(valuesOf(FilterOperator.values())))
                        .multipleConditionsSupported(true)
                        .build()))
                .build();
    }

    private NodeCapabilityDTO sortCapability() {
        return NodeCapabilityDTO.builder()
                .code("sort")
                .name("Sort Capability")
                .capabilityConfig(CapabilityConfigDTO.builder()
                        .sqlPushdownSupported(true)
                        .pushdownBoundary("ORDER_BY_LIMIT_WITH_FALLBACK")
                        .inputConstraints(List.of("datasetRef required"))
                        .outputGuarantees(List.of("sorted dataset", "optional limit applied"))
                        .extensions(Map.of("explainable", true, "auditable", true))
                        .build())
                .params(toMap(SortCapabilityParamsDTO.builder()
                        .multiFieldSupported(true)
                        .limitSupported(true)
                        .build()))
                .build();
    }

    private NodeCapabilityDTO formulaCapability() {
        return NodeCapabilityDTO.builder()
                .code("formula")
                .name("Derived Metric Capability")
                .capabilityConfig(CapabilityConfigDTO.builder()
                        .sqlPushdownSupported(false)
                        .pushdownBoundary("IN_MEMORY_DERIVED_METRIC")
                        .inputConstraints(List.of("datasetRef required", "formulas required"))
                        .outputGuarantees(List.of("dataset with appended derived metrics"))
                        .extensions(Map.of("explainable", true, "auditable", true, "derivedMetric", true))
                        .build())
                .params(toMap(FormulaCapabilityParamsDTO.builder()
                        .derivedMetric(true)
                        .supportedOperators(List.of("+", "-", "*", "/"))
                        .parenthesisSupported(true)
                        .build()))
                .build();
    }

    private Map<String, Object> toMap(Object value) {
        return objectMapper.convertValue(value, Map.class);
    }

    private String[] valuesOf(Enum<?>[] values) {
        String[] result = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i].name();
        }
        return result;
    }
}
