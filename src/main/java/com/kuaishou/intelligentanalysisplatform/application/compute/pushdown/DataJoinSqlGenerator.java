package com.kuaishou.intelligentanalysisplatform.application.compute.pushdown;

import java.util.List;
import java.util.stream.Collectors;

import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.enums.JoinType;
import com.kuaishou.intelligentanalysisplatform.contract.schema.JoinCondition;
import org.springframework.stereotype.Component;

@Component
public class DataJoinSqlGenerator {

    /**
     * 生成 SQL JOIN 语句，用于同源数据集的下推执行。
     *
     * @param leftSql       左表子查询 SQL
     * @param rightSql      右表子查询 SQL
     * @param joinType      JOIN 类型
     * @param conditions    JOIN 条件列表
     * @param selectColumns 选择的列，null 表示全选
     * @param dialect       数据源方言
     * @return 生成的 SQL 字符串
     */
    public String generate(String leftSql, String rightSql,
                           JoinType joinType, List<JoinCondition> conditions,
                           List<String> selectColumns, DatasourceDialect dialect) {
        if (joinType == null) {
            throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "joinType must not be null");
        }
        if (conditions == null || conditions.isEmpty()) {
            throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT,
                    "JOIN conditions (on) must not be empty for SQL JOIN generation");
        }
        if (dialect == DatasourceDialect.CLICKHOUSE && joinType == JoinType.FULL) {
            throw new BaseBusinessException(ErrorCode.NOT_IMPLEMENTED,
                    "ClickHouse does not support FULL OUTER JOIN; use INNER, LEFT, or RIGHT JOIN instead");
        }

        String joinKeyword = switch (joinType) {
            case INNER -> "INNER JOIN";
            case LEFT  -> "LEFT JOIN";
            case RIGHT -> "RIGHT JOIN";
            case FULL  -> "FULL OUTER JOIN";
        };

        String onClause = conditions.stream()
                .map(c -> "_left." + c.getLeftField() + " = _right." + c.getRightField())
                .collect(Collectors.joining(" AND "));

        String selectClause = (selectColumns == null || selectColumns.isEmpty())
                ? "_left.*, _right.*"
                : selectColumns.stream().collect(Collectors.joining(", "));

        return String.format(
                "SELECT %s FROM (%s) AS _left %s (%s) AS _right ON %s",
                selectClause, leftSql, joinKeyword, rightSql, onClause
        );
    }
}
