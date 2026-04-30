package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryRequestDTO {
    private String requestId;
    private String datasourceId;
    private String sql;
    private Map<String, Object> parameters;
    private QueryOptionDTO option;
    private RequestContextDTO context;
}
