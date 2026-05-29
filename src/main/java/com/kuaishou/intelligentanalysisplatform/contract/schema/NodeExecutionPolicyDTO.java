package com.kuaishou.intelligentanalysisplatform.contract.schema;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeExecutionPolicyDTO {
    private ExecutionMode preferredMode;
    private Boolean allowAsync;
    private Boolean allowSync;
    private Boolean heavyTask;
}
