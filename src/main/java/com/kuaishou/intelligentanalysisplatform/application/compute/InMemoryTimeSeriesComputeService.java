package com.kuaishou.intelligentanalysisplatform.application.compute;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.kuaishou.intelligentanalysisplatform.contract.enums.CompareUnit;
import com.kuaishou.intelligentanalysisplatform.contract.enums.TimeSeriesComputeType;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.MetricComputeRuleDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.TimeSeriesComputeNodeConfigDTO;
import org.springframework.stereotype.Service;

@Service
public class InMemoryTimeSeriesComputeService {
    public DatasetDTO compute(TimeSeriesComputeNodeConfigDTO config, DatasetDTO input) {
        List<Map<String, Object>> rows = input == null || input.getRows() == null ? List.of() : input.getRows();
        Map<List<Object>, Map<String, Object>> periodRows = aggregateRows(config, rows);
        List<Map<String, Object>> resultRows = new ArrayList<>();
        for (Map.Entry<List<Object>, Map<String, Object>> entry : periodRows.entrySet()) {
            Map<String, Object> resultRow = new LinkedHashMap<>(entry.getValue());
            String period = String.valueOf(resultRow.get("period"));
            for (MetricComputeRuleDTO rule : config.getMetrics()) {
                String comparePeriod = comparePeriod(period, config, rule);
                Map<String, Object> previousRow = periodRows.get(buildKey(resultRow, config.getDimensionFields(), comparePeriod));
                applyRule(resultRow, rule, periodRows, config, currentValue(resultRow, rule), previousValue(previousRow, rule), comparePeriod);
            }
            resultRows.add(resultRow);
        }
        List<FieldSchemaDTO> fields = new ArrayList<>();
        fields.add(ComputeSupport.timeField(config.getTimeField()));
        fields.add(ComputeSupport.dimensionField("period"));
        for (String dimensionField : config.getDimensionFields() == null ? List.<String>of() : config.getDimensionFields()) {
            fields.add(ComputeSupport.dimensionField(dimensionField));
        }
        for (MetricComputeRuleDTO rule : config.getMetrics()) {
            fields.add(ComputeSupport.metricField(rule.getMetricField()));
            fields.add(ComputeSupport.metricField(resolveAlias(rule)));
            fields.add(ComputeSupport.metricField(resolveAlias(rule) + "_previous"));
            fields.add(ComputeSupport.dimensionField(resolveAlias(rule) + "_compare_period"));
        }
        return ComputeSupport.dataset(resultRows, fields, Map.of(
                "inputRowCount", rows.size(),
                "outputRowCount", resultRows.size(),
                "granularity", config.getGranularity().name()));
    }

    private Map<List<Object>, Map<String, Object>> aggregateRows(TimeSeriesComputeNodeConfigDTO config, List<Map<String, Object>> rows) {
        Map<List<Object>, Map<String, Object>> aggregated = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String period = ComputeSupport.truncatePeriod(row.get(config.getTimeField()), config.getGranularity());
            List<Object> key = buildKey(row, config.getDimensionFields(), period);
            Map<String, Object> target = aggregated.computeIfAbsent(key, ignored -> initRow(row, config, period));
            for (MetricComputeRuleDTO rule : config.getMetrics()) {
                BigDecimal existing = ComputeSupport.toDecimal(target.get(rule.getMetricField()));
                BigDecimal current = ComputeSupport.toDecimal(row.get(rule.getMetricField()));
                target.put(rule.getMetricField(), sum(existing, current));
            }
        }
        return aggregated;
    }

    private Map<String, Object> initRow(Map<String, Object> row, TimeSeriesComputeNodeConfigDTO config, String period) {
        Map<String, Object> target = new LinkedHashMap<>();
        target.put(config.getTimeField(), row.get(config.getTimeField()));
        target.put("period", period);
        if (config.getDimensionFields() != null) {
            for (String dimensionField : config.getDimensionFields()) {
                target.put(dimensionField, row.get(dimensionField));
            }
        }
        return target;
    }

    private void applyRule(Map<String, Object> row,
                           MetricComputeRuleDTO rule,
                           Map<List<Object>, Map<String, Object>> periodRows,
                           TimeSeriesComputeNodeConfigDTO config,
                           BigDecimal current,
                           BigDecimal previous,
                           String comparePeriod) {
        String alias = resolveAlias(rule);
        row.put(alias + "_previous", previous);
        row.put(alias + "_compare_period", comparePeriod);
        if (rule.getComputeType() == TimeSeriesComputeType.MOVING_AVG) {
            row.put(alias, movingAverage(row, rule, periodRows, config));
            return;
        }
        if (rule.getComputeType() == TimeSeriesComputeType.DELTA) {
            row.put(alias, previous == null || current == null ? null : current.subtract(previous));
            return;
        }
        row.put(alias, ComputeSupport.ratio(current, previous));
    }

    private BigDecimal movingAverage(Map<String, Object> row,
                                     MetricComputeRuleDTO rule,
                                     Map<List<Object>, Map<String, Object>> periodRows,
                                     TimeSeriesComputeNodeConfigDTO config) {
        int windowSize = rule.getWindowSize() == null || rule.getWindowSize() <= 0 ? 3 : rule.getWindowSize();
        String period = String.valueOf(row.get("period"));
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (int index = 0; index < windowSize; index++) {
            String windowPeriod = ComputeSupport.shiftPeriod(period, config.getGranularity(), index);
            Map<String, Object> windowRow = periodRows.get(buildKey(row, config.getDimensionFields(), windowPeriod));
            BigDecimal value = previousValue(windowRow, rule);
            if (value != null) {
                sum = sum.add(value);
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        return sum.divide(BigDecimal.valueOf(count), 4, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal currentValue(Map<String, Object> row, MetricComputeRuleDTO rule) {
        return ComputeSupport.toDecimal(row.get(rule.getMetricField()));
    }

    private BigDecimal previousValue(Map<String, Object> row, MetricComputeRuleDTO rule) {
        return row == null ? null : ComputeSupport.toDecimal(row.get(rule.getMetricField()));
    }

    private BigDecimal sum(BigDecimal left, BigDecimal right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.add(right);
    }

    private String resolveAlias(MetricComputeRuleDTO rule) {
        return rule.getAlias() == null || rule.getAlias().isBlank() ? rule.getMetricField() + "_" + rule.getComputeType().name().toLowerCase() : rule.getAlias();
    }

    private String comparePeriod(String period, TimeSeriesComputeNodeConfigDTO config, MetricComputeRuleDTO rule) {
        int shift = rule.getCompareShift() == null || rule.getCompareShift() <= 0 ? 1 : rule.getCompareShift();
        if (rule.getCompareUnit() == CompareUnit.YEAR) {
            return ComputeSupport.shiftPeriod(period, com.kuaishou.intelligentanalysisplatform.contract.enums.TimeGranularity.YEAR, shift);
        }
        return ComputeSupport.shiftPeriod(period, config.getGranularity(), shift);
    }

    private List<Object> buildKey(Map<String, Object> row, List<String> dimensions, String period) {
        List<Object> key = new ArrayList<>();
        key.add(period);
        if (dimensions != null) {
            key.addAll(dimensions.stream().map(row::get).filter(Objects::nonNull).collect(Collectors.toList()));
        }
        return key;
    }
}
