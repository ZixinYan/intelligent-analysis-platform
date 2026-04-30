package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DebugOptionDTO {
    private Boolean includeTrace;
    private Boolean dryRun;
}
