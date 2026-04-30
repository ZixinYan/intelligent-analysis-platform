package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ValidateResultDTO {
    private String queryId;
    private boolean valid;
    private String normalizedSql;
    private String sqlFingerprint;
    private List<String> violationCodes;
    private String message;
    private Long validatedAt;
}
