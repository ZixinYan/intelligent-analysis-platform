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
public class DatasetStatDTO {
    private Integer rowCount;
    private Integer returnedRowCount;
    private Boolean truncated;
    private Map<String, Object> extensions;
}
