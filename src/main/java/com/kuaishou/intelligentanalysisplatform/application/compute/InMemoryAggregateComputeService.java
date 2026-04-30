package com.kuaishou.intelligentanalysisplatform.application.compute;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.kuaishou.intelligentanalysisplatform.contract.enums.AggregateFunction;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AggregateMetricDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.AggregateNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import org.springframework.stereotype.Service;

@Service
public class InMemoryAggregateComputeService {
    public DatasetDTO compute(AggregateNodeConfigDTO config, DatasetDTO input) {
        List<Map<String, Object>> rows = input == null || input.getRows() == null ? List.of() : input.getRows();
        Map<List<Object>, List<Map<String, Object>>> grouped = rows.stream().collect(Collectors.groupingBy(
                row -> buildKey(row, config.getGroupByFields()), LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> resultRows = new ArrayList<>();
        for (Map.Entry<List<Object>, List<Map<String, Object>>> entry : grouped.entrySet()) {
            Map<String, Object> resultRow = new LinkedHashMap<>();
            List<String> groupByFields = config.getGroupByFields() == null ? List.of() : config.getGroupByFields();
            for (int index = 0; index < groupByFields.size(); index++) {
                resultRow.put(groupByFields.get(index), entry.getKey().get(index));
            }
            for (AggregateMetricDTO metric : config.getMetrics()) {
                resultRow.put(resolveMetricName(metric), aggregate(metric, entry.getValue()));
            }
            resultRows.add(resultRow);
        }
        if (ComputeSupport.sortComparator(config.getSortFields()) != null) {
            resultRows = resultRows.stream().sorted(ComputeSupport.sortComparator(config.getSortFields())).collect(Collectors.toList());
        }
        if (config.getTopN() != null && config.getTopN() > 0 && resultRows.size() > config.getTopN()) {
            resultRows = resultRows.subList(0, config.getTopN());
        }
        List<FieldSchemaDTO> fields = new ArrayList<>();
        for (String field : config.getGroupByFields() == null ? List.<String>of() : config.getGroupByFields()) {
            fields.add(ComputeSupport.dimensionField(field));
        }
        for (AggregateMetricDTO metric : config.getMetrics()) {
            fields.add(ComputeSupport.metricField(resolveMetricName(metric)));
        }
        return ComputeSupport.dataset(resultRows, fields, Map.of(
                "inputRowCount", rows.size(),
                "outputRowCount", resultRows.size(),
                "groupCount", grouped.size()));
    }

    private List<Object> buildKey(Map<String, Object> row, List<String> groupByFields) {
        if (groupByFields == null || groupByFields.isEmpty()) {
            return List.of("__all__");
        }
        return groupByFields.stream().map(row::get).collect(Collectors.toList());
    }

    private Object aggregate(AggregateMetricDTO metric, List<Map<String, Object>> rows) {
        AggregateFunction function = metric.getAgg();
        return switch (function) {
            case COUNT -> rows.size();
            case COUNT_DISTINCT -> rows.stream().map(row -> row.get(metric.getField())).filter(Objects::nonNull).distinct().count();
            case MAX -> rows.stream().map(row -> ComputeSupport.toDecimal(row.get(metric.getField()))).filter(Objects::nonNull).max(BigDecimal::compareTo).orElse(null);
            case MIN -> rows.stream().map(row -> ComputeSupport.toDecimal(row.get(metric.getField()))).filter(Objects::nonNull).min(BigDecimal::compareTo).orElse(null);
            case AVG -> {
                List<BigDecimal> values = rows.stream().map(row -> ComputeSupport.toDecimal(row.get(metric.getField()))).filter(Objects::nonNull).toList();
                if (values.isEmpty()) {
                    yield null;
                }
                BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                yield sum.divide(BigDecimal.valueOf(values.size()), 4, java.math.RoundingMode.HALF_UP);
            }
            case SUM -> rows.stream().map(row -> ComputeSupport.toDecimal(row.get(metric.getField()))).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        };
    }

    private String resolveMetricName(AggregateMetricDTO metric) {
        return metric.getAlias() == null || metric.getAlias().isBlank() ? metric.getField() + "_" + metric.getAgg().name().toLowerCase() : metric.getAlias();
    }
}
