package com.kuaishou.intelligentanalysisplatform.domain.query.model;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SqlGuardDecision {
    private boolean allowed;
    private GuardAction action;
    private List<GuardViolationCode> violationCodes;
    private String normalizedSql;
    private String sqlFingerprint;
    private String message;
}
