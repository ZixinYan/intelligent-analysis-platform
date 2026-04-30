package com.kuaishou.intelligentanalysisplatform.infra.query.guard;

import com.kuaishou.intelligentanalysisplatform.domain.query.model.GuardViolationCode;
import com.kuaishou.intelligentanalysisplatform.domain.query.model.QueryGovernancePolicy;
import com.kuaishou.intelligentanalysisplatform.domain.query.model.QueryGuardContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedSqlGuardTest {

    private final RuleBasedSqlGuard sqlGuard = new RuleBasedSqlGuard();

    @Test
    void shouldAllowCommentedSelect() {
        assertThat(sqlGuard.validate(context("/* comment */ select 1", null, true)).isAllowed()).isTrue();
    }

    @Test
    void shouldRejectNullContext() {
        assertThat(sqlGuard.validate(null).getViolationCodes()).contains(GuardViolationCode.SQL_PARSE_FAILED);
    }

    @Test
    void shouldRejectMultiStatement() {
        assertThat(sqlGuard.validate(context("select 1; delete from t", null, true)).getViolationCodes())
                .contains(GuardViolationCode.SQL_MULTI_STATEMENT_REJECTED);
    }

    @Test
    void shouldRejectLockClause() {
        assertThat(sqlGuard.validate(context("select * from t for update", null, true)).getViolationCodes())
                .contains(GuardViolationCode.SQL_LOCK_CLAUSE_FORBIDDEN);
    }

    @Test
    void shouldRejectNonPositiveLimit() {
        assertThat(sqlGuard.validate(context("select 1", 0, true)).getViolationCodes())
                .contains(GuardViolationCode.QUERY_LIMIT_EXCEEDED);
    }

    private QueryGuardContext context(String sql, Integer limit, boolean preview) {
        return QueryGuardContext.builder()
                .queryId("q1")
                .sql(sql)
                .requestedLimit(limit)
                .preview(preview)
                .policy(QueryGovernancePolicy.defaultPolicy())
                .build();
    }
}
