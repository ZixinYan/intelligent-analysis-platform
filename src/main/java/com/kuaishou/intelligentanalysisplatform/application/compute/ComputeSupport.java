package com.kuaishou.intelligentanalysisplatform.application.compute;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import com.kuaishou.intelligentanalysisplatform.contract.enums.FieldSemanticType;
import com.kuaishou.intelligentanalysisplatform.contract.enums.TimeGranularity;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ValueType;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetStatDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SortFieldDTO;

public final class ComputeSupport {
    private ComputeSupport() {
    }

    public static DatasetDTO dataset(List<Map<String, Object>> rows,
                                     List<FieldSchemaDTO> fields,
                                     Map<String, Object> statExtensions) {
        DatasetStatDTO stat = DatasetStatDTO.builder()
                .rowCount(rows.size())
                .returnedRowCount(rows.size())
                .truncated(false)
                .extensions(statExtensions)
                .build();
        return DatasetDTO.builder()
                .rows(rows)
                .schema(DatasetSchemaDTO.builder()
                        .fields(fields == null ? List.of() : fields)
                        .metrics(List.of())
                        .dimensions(List.of())
                        .timeFields(List.of())
                        .build())
                .stat(stat)
                .build();
    }

    public static FieldSchemaDTO dimensionField(String name) {
        return FieldSchemaDTO.builder()
                .fieldId(name)
                .name(name)
                .displayName(name)
                .valueType(ValueType.STRING)
                .nullable(true)
                .semanticType(FieldSemanticType.DIMENSION)
                .build();
    }

    public static FieldSchemaDTO timeField(String name) {
        return FieldSchemaDTO.builder()
                .fieldId(name)
                .name(name)
                .displayName(name)
                .valueType(ValueType.STRING)
                .nullable(true)
                .semanticType(FieldSemanticType.TIME_DIMENSION)
                .build();
    }

    public static FieldSchemaDTO metricField(String name) {
        return FieldSchemaDTO.builder()
                .fieldId(name)
                .name(name)
                .displayName(name)
                .valueType(ValueType.DECIMAL)
                .nullable(true)
                .semanticType(FieldSemanticType.METRIC)
                .build();
    }

    public static Comparator<Map<String, Object>> sortComparator(List<SortFieldDTO> sortFields) {
        if (sortFields == null || sortFields.isEmpty()) {
            return null;
        }
        Comparator<Map<String, Object>> comparator = null;
        for (SortFieldDTO sortField : sortFields) {
            Comparator<Map<String, Object>> current = (left, right) -> compareValues(left.get(sortField.getField()), right.get(sortField.getField()));
            if (sortField.getOrder() != null && "DESC".equalsIgnoreCase(sortField.getOrder())) {
                current = current.reversed();
            }
            comparator = comparator == null ? current : comparator.thenComparing(current);
        }
        return comparator;
    }

    public static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static BigDecimal ratio(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || BigDecimal.ZERO.compareTo(previous) == 0) {
            return null;
        }
        return current.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous, 2, RoundingMode.HALF_UP);
    }

    public static String truncatePeriod(Object value, TimeGranularity granularity) {
        LocalDate date = toDate(value);
        if (date == null) {
            return null;
        }
        return switch (granularity) {
            case DAY -> date.toString();
            case WEEK -> date.with(DayOfWeek.MONDAY).toString();
            case MONTH -> YearMonth.from(date).toString();
            case QUARTER -> date.getYear() + "-Q" + ((date.getMonthValue() - 1) / 3 + 1);
            case YEAR -> String.valueOf(date.getYear());
        };
    }

    public static String shiftPeriod(String period, TimeGranularity granularity, int offset) {
        if (period == null) {
            return null;
        }
        return switch (granularity) {
            case DAY -> LocalDate.parse(period).minusDays(offset).toString();
            case WEEK -> LocalDate.parse(period).minusWeeks(offset).toString();
            case MONTH -> YearMonth.parse(period).minusMonths(offset).toString();
            case QUARTER -> shiftQuarter(period, offset);
            case YEAR -> String.valueOf(Integer.parseInt(period) - offset);
        };
    }

    public static LocalDate toDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toLocalDate();
        }
        String text = String.valueOf(value);
        try {
            return LocalDate.parse(text);
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(text).toLocalDate();
        } catch (Exception ignored) {
        }
        try {
            return LocalDate.parse(text, DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.getDefault()));
        } catch (Exception ignored) {
        }
        return null;
    }

    public static List<Map<String, Object>> copyRows(List<Map<String, Object>> rows) {
        if (rows == null) {
            return List.of();
        }
        return rows.stream().map(LinkedHashMap::new).collect(Collectors.toList());
    }

    public static List<String> tokenizeExpression(String expression) {
        StringTokenizer tokenizer = new StringTokenizer(expression, "+-*/() ", true);
        List<String> tokens = new ArrayList<>();
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken().trim();
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static int compareValues(Object left, Object right) {
        BigDecimal leftDecimal = toDecimal(left);
        BigDecimal rightDecimal = toDecimal(right);
        if (leftDecimal != null && rightDecimal != null) {
            return leftDecimal.compareTo(rightDecimal);
        }
        String leftText = left == null ? "" : String.valueOf(left);
        String rightText = right == null ? "" : String.valueOf(right);
        return leftText.compareTo(rightText);
    }

    private static String shiftQuarter(String period, int offset) {
        String[] parts = period.split("-Q");
        int year = Integer.parseInt(parts[0]);
        int quarter = Integer.parseInt(parts[1]);
        int total = year * 4 + quarter - offset;
        int targetYear = (total - 1) / 4;
        int targetQuarter = (total - 1) % 4 + 1;
        return targetYear + "-Q" + targetQuarter;
    }
}
