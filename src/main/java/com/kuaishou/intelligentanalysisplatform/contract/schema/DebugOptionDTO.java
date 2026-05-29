package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebugOptionDTO {
    private Boolean includeTrace;
    private Boolean dryRun;
}
