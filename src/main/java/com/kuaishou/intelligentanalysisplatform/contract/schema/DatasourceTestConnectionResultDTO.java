package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasourceTestConnectionResultDTO {
    private Boolean success;
    private Long latencyMs;
    private String message;
    private String serverVersion;
}
