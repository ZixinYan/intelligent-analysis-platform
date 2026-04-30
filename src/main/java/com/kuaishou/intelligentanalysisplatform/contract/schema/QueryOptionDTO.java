package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryOptionDTO {
    private Integer timeoutMs;
    private Integer limit;
    private Integer offset;
    private Integer pageSize;
    private String cursor;
    private Boolean useCache;
    private Integer cacheTtlSeconds;
    private Boolean readOnly;
    private Boolean asyncPreferred;
}
