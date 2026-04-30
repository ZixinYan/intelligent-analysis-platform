package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DatasetStatDTO {
    private Integer rowCount;
    private Integer returnedRowCount;
    private Boolean truncated;
    private Map<String, Object> extensions;
}
