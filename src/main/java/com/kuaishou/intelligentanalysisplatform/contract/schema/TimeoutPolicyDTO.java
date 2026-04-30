package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TimeoutPolicyDTO {
    private Integer timeoutMs;
    private Boolean failFast;
}
