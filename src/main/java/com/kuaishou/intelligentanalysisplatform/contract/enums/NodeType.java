package com.kuaishou.intelligentanalysisplatform.contract.enums;

public enum NodeType {
    SQL_QUERY("sql_query", NodeCategory.QUERY),
    DATASET_READ("dataset_read", NodeCategory.QUERY),
    CACHED_QUERY("cached_query", NodeCategory.QUERY),
    AGGREGATE("aggregate", NodeCategory.COMPUTE),
    TIME_SERIES_COMPUTE("time_series_compute", NodeCategory.COMPUTE),
    PIVOT("pivot", NodeCategory.COMPUTE),
    FILTER("filter", NodeCategory.COMPUTE),
    SORT("sort", NodeCategory.COMPUTE),
    FORMULA("formula", NodeCategory.COMPUTE),
    PYTHON_SCRIPT("python_script", NodeCategory.COMPUTE),
    JAVA_CODE("java_code", NodeCategory.COMPUTE),
    DATA_JOIN("data_join", NodeCategory.COMPUTE),
    CHART_OUTPUT("chart_output", NodeCategory.OUTPUT),
    TABLE_OUTPUT("table_output", NodeCategory.OUTPUT),
    EXPORT_OUTPUT("export_output", NodeCategory.OUTPUT),
    DATA_LIMIT("data_limit", NodeCategory.GOVERNANCE),
    APPROVAL_GATE("approval_gate", NodeCategory.GOVERNANCE),
    CACHE_POLICY("cache_policy", NodeCategory.GOVERNANCE),
    MASKING("masking", NodeCategory.GOVERNANCE),
    CONDITION("condition", NodeCategory.GOVERNANCE),
    ERROR_HANDLER("error_handler", NodeCategory.GOVERNANCE),
    KNOWLEDGE_RETRIEVAL("knowledge_retrieval", NodeCategory.QUERY);

    private final String code;
    private final NodeCategory category;

    NodeType(String code, NodeCategory category) {
        this.code = code;
        this.category = category;
    }

    public String getCode() {
        return code;
    }

    public NodeCategory getCategory() {
        return category;
    }
}
