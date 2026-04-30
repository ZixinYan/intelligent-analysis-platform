package com.kuaishou.intelligentanalysisplatform.domain.query.service;

import com.kuaishou.intelligentanalysisplatform.domain.query.model.QueryGuardContext;
import com.kuaishou.intelligentanalysisplatform.domain.query.model.SqlGuardDecision;

public interface SqlGuard {
    SqlGuardDecision validate(QueryGuardContext context);
}
