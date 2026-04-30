package com.kuaishou.intelligentanalysisplatform.domain.query.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QueryGuardContext {
    private String queryId;
    private String tenantId;
    private String operatorId;
    private String datasourceId;
    private String sql;
    private Integer requestedLimit;
    private Integer timeoutMs;
    private boolean preview;
    private QueryGovernancePolicy policy;
}
