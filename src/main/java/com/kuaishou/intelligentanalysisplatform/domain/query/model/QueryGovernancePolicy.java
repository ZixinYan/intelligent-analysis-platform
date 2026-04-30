package com.kuaishou.intelligentanalysisplatform.domain.query.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QueryGovernancePolicy {
    private Integer defaultPreviewTimeoutMs;
    private Integer defaultRunTimeoutMs;
    private Integer maxTimeoutMs;
    private Integer previewMaxRows;
    private Integer runMaxRows;
    private Integer maxColumns;
    private Integer maxBytes;
    private Integer maxCellLength;
    private Integer slowQueryThresholdMs;
    private Integer tenantQpsLimit;
    private Integer datasourceConcurrencyLimit;

    public static QueryGovernancePolicy defaultPolicy() {
        return QueryGovernancePolicy.builder()
                .defaultPreviewTimeoutMs(10000)
                .defaultRunTimeoutMs(30000)
                .maxTimeoutMs(30000)
                .previewMaxRows(200)
                .runMaxRows(5000)
                .maxColumns(200)
                .maxBytes(10 * 1024 * 1024)
                .maxCellLength(4096)
                .slowQueryThresholdMs(5000)
                .tenantQpsLimit(20)
                .datasourceConcurrencyLimit(10)
                .build();
    }
}
