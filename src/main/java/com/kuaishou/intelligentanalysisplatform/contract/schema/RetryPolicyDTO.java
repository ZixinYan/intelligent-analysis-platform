package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RetryPolicyDTO {
    private Integer maxRetries;
    private Integer backoffMs;
    private Boolean retryable;
}
