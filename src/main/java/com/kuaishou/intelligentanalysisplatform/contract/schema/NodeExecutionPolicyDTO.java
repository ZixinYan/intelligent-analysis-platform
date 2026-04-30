package com.kuaishou.intelligentanalysisplatform.contract.schema;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionMode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NodeExecutionPolicyDTO {
    private ExecutionMode preferredMode;
    private Boolean allowAsync;
    private Boolean allowSync;
    private Boolean heavyTask;
}
