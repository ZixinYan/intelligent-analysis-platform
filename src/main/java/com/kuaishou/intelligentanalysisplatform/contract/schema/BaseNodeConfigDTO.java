package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
public abstract class BaseNodeConfigDTO {
    private String title;
    private String description;
    private NodeExecutionPolicyDTO executionPolicy;
    private RetryPolicyDTO retryPolicy;
    private TimeoutPolicyDTO timeoutPolicy;
    private CachePolicyDTO cachePolicy;
    private List<NodeInputBindingDTO> inputs;
    private List<NodeOutputDefinitionDTO> outputs;
}
