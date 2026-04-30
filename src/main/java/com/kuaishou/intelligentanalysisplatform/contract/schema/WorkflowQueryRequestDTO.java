package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowQueryRequestDTO {
    @Builder.Default
    private Integer page = 1;
    @Builder.Default
    private Integer pageSize = 20;
    private RequestContextDTO context;
}
