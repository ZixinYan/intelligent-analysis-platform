package com.kuaishou.intelligentanalysisplatform.domain.query.connector;

import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QueryCommand {
    private String queryId;
    private String normalizedSql;
    private Map<String, Object> parameters;
    private Integer timeoutMs;
    private Integer maxRows;
    private PaginationMode paginationMode;
    private Integer offset;
    private Integer pageSize;
    private String cursor;
}
