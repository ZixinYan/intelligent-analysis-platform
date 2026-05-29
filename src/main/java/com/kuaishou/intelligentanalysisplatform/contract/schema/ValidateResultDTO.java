package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateResultDTO {
    private String queryId;
    private boolean valid;
    private String normalizedSql;
    private String sqlFingerprint;
    private List<String> violationCodes;
    private String message;
    private Long validatedAt;
}
