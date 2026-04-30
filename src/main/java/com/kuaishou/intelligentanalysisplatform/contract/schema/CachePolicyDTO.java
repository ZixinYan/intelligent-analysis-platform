package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CachePolicyDTO {
    private Boolean enabled;
    private Integer ttlSeconds;
    private String scope;
}
