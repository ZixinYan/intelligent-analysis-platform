package com.kuaishou.intelligentanalysisplatform.application.compute.pushdown;

import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceType;

public enum DatasourceDialect {
    MYSQL, CLICKHOUSE, POSTGRES;

    public static DatasourceDialect from(DatasourceType type) {
        if (type == null) {
            return MYSQL;
        }
        return switch (type) {
            case MYSQL -> MYSQL;
            case CLICKHOUSE -> CLICKHOUSE;
            case POSTGRES -> POSTGRES;
        };
    }

    /**
     * 返回各方言的日期截断函数表达式（供未来时序下推使用）。
     * MySQL=DATE_FORMAT, Postgres=date_trunc, ClickHouse=toStartOfDay/toStartOfMonth 等。
     */
    public String dateTruncExpr(String field, String granularity) {
        return switch (this) {
            case MYSQL -> switch (granularity.toUpperCase()) {
                case "DAY" -> "DATE_FORMAT(" + field + ", '%Y-%m-%d')";
                case "MONTH" -> "DATE_FORMAT(" + field + ", '%Y-%m')";
                case "YEAR" -> "DATE_FORMAT(" + field + ", '%Y')";
                case "HOUR" -> "DATE_FORMAT(" + field + ", '%Y-%m-%d %H:00:00')";
                default -> "DATE(" + field + ")";
            };
            case POSTGRES -> switch (granularity.toUpperCase()) {
                case "DAY" -> "date_trunc('day', " + field + ")";
                case "MONTH" -> "date_trunc('month', " + field + ")";
                case "YEAR" -> "date_trunc('year', " + field + ")";
                case "HOUR" -> "date_trunc('hour', " + field + ")";
                default -> "date_trunc('day', " + field + ")";
            };
            case CLICKHOUSE -> switch (granularity.toUpperCase()) {
                case "DAY" -> "toStartOfDay(" + field + ")";
                case "MONTH" -> "toStartOfMonth(" + field + ")";
                case "YEAR" -> "toStartOfYear(" + field + ")";
                case "HOUR" -> "toStartOfHour(" + field + ")";
                default -> "toStartOfDay(" + field + ")";
            };
        };
    }
}
