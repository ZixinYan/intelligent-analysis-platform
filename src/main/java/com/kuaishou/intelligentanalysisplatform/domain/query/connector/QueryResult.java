package com.kuaishou.intelligentanalysisplatform.domain.query.connector;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QueryResult {
    private List<FieldSchemaDTO> fields;
    private List<Map<String, Object>> rows;
    private Integer rowCount;
    private Boolean truncated;
    private String nextCursor;
    private Long elapsedMs;
    private Boolean cached;
}
