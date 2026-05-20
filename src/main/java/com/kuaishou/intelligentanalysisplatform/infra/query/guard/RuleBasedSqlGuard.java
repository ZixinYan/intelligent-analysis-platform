package com.kuaishou.intelligentanalysisplatform.infra.query.guard;

import com.kuaishou.intelligentanalysisplatform.domain.query.model.GuardAction;
import com.kuaishou.intelligentanalysisplatform.domain.query.model.GuardViolationCode;
import com.kuaishou.intelligentanalysisplatform.domain.query.model.QueryGovernancePolicy;
import com.kuaishou.intelligentanalysisplatform.domain.query.model.QueryGuardContext;
import com.kuaishou.intelligentanalysisplatform.domain.query.model.SqlGuardDecision;
import com.kuaishou.intelligentanalysisplatform.domain.query.service.SqlGuard;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class RuleBasedSqlGuard implements SqlGuard {

    /**
     * 执行SQL或者查询命令的黑名单，这里应该只读
     * 建议配置层面使用只读的用户，这里算是个兜底吧
     */
    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "insert", "update", "delete", "merge", "replace",
            "create", "alter", "drop", "truncate", "rename", "comment",
            "grant", "revoke", "commit", "rollback", "savepoint",
            "set", "use", "reset", "call", "exec", "execute",
            "copy", "show", "describe", "desc", "vacuum", "optimize", "analyze"
    );

    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
            Pattern.compile("\\binto\\s+outfile\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bload\\s+data\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bpg_sleep\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bsleep\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bbenchmark\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bmysql\\.user\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bpg_authid\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bxp_cmdshell\\b", Pattern.CASE_INSENSITIVE)
    );

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("(?m)--[^\\n]*$");

    @Override
    public SqlGuardDecision validate(QueryGuardContext context) {
        if (context == null) {
            return reject("", List.of(GuardViolationCode.SQL_PARSE_FAILED), "guard context is null");
        }
        String sql = normalize(context.getSql());
        List<GuardViolationCode> violations = new ArrayList<>();
        if (sql.isBlank()) {
            return reject(sql, List.of(GuardViolationCode.SQL_PARSE_FAILED), "sql is blank");
        }
        if (isMultiStatement(sql)) {
            violations.add(GuardViolationCode.SQL_MULTI_STATEMENT_REJECTED);
        }
        String leadingKeyword = extractLeadingKeyword(sql);
        if (leadingKeyword == null) {
            violations.add(GuardViolationCode.SQL_PARSE_FAILED);
        } else if (FORBIDDEN_KEYWORDS.contains(leadingKeyword) || startsWithForbiddenPhrase(sql)) {
            violations.add(GuardViolationCode.SQL_FORBIDDEN_STATEMENT);
        }
        if (!isReadonlyQuery(leadingKeyword)) {
            violations.add(GuardViolationCode.SQL_NOT_READONLY);
        }
        if (hasLockClause(sql)) {
            violations.add(GuardViolationCode.SQL_LOCK_CLAUSE_FORBIDDEN);
        }
        if (hasDangerousPattern(sql)) {
            violations.add(GuardViolationCode.SQL_FORBIDDEN_STATEMENT);
        }
        if (isLimitExceeded(context.getRequestedLimit(), context.getPolicy(), context.isPreview())) {
            violations.add(GuardViolationCode.QUERY_LIMIT_EXCEEDED);
        }
        if (!violations.isEmpty()) {
            return reject(sql, violations, "sql rejected by guard");
        }
        return SqlGuardDecision.builder()
                .allowed(true)
                .action(GuardAction.ALLOW)
                .violationCodes(List.of())
                .normalizedSql(sql)
                .sqlFingerprint(fingerprint(sql))
                .message("allowed")
                .build();
    }

    private SqlGuardDecision reject(String sql, List<GuardViolationCode> violations, String message) {
        return SqlGuardDecision.builder()
                .allowed(false)
                .action(GuardAction.REJECT)
                .violationCodes(violations.stream().distinct().toList())
                .normalizedSql(sql)
                .sqlFingerprint(fingerprint(sql))
                .message(message)
                .build();
    }

    private String normalize(String sql) {
        if (sql == null) {
            return "";
        }
        String withoutBlockComment = BLOCK_COMMENT.matcher(sql).replaceAll(" ");
        String withoutLineComment = LINE_COMMENT.matcher(withoutBlockComment).replaceAll(" ");
        return withoutLineComment.trim().replaceAll("\\s+", " ").replaceAll(";\\s*$", "");
    }

    private boolean isMultiStatement(String sql) {
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (c == ';' && !inSingle && !inDouble) {
                String suffix = sql.substring(i + 1).trim();
                if (!suffix.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private String extractLeadingKeyword(String sql) {
        int index = 0;
        while (index < sql.length() && !Character.isLetter(sql.charAt(index))) {
            index++;
        }
        if (index >= sql.length()) {
            return null;
        }
        int end = index;
        while (end < sql.length() && Character.isLetter(sql.charAt(end))) {
            end++;
        }
        return sql.substring(index, end).toLowerCase();
    }

    private boolean startsWithForbiddenPhrase(String sql) {
        String lower = sql.toLowerCase();
        return lower.startsWith("load data")
                || lower.startsWith("lock table")
                || lower.startsWith("unlock table")
                || lower.startsWith("set transaction")
                || lower.startsWith("explain analyze");
    }

    private boolean isReadonlyQuery(String leadingKeyword) {
        return "select".equals(leadingKeyword) || "with".equals(leadingKeyword);
    }

    private boolean hasLockClause(String sql) {
        String lower = sql.toLowerCase();
        return lower.contains(" for update")
                || lower.contains(" lock in share mode")
                || lower.contains(" for share")
                || lower.contains(" for no key update")
                || lower.contains(" skip locked")
                || lower.contains(" nowait");
    }

    private boolean hasDangerousPattern(String sql) {
        return DANGEROUS_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(sql).find());
    }

    private boolean isLimitExceeded(Integer requestedLimit, QueryGovernancePolicy policy, boolean preview) {
        if (requestedLimit == null || policy == null) {
            return false;
        }
        if (requestedLimit <= 0) {
            return true;
        }
        Integer max = preview ? policy.getPreviewMaxRows() : policy.getRunMaxRows();
        return max != null && requestedLimit > max;
    }

    private String fingerprint(String sql) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(sql.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
