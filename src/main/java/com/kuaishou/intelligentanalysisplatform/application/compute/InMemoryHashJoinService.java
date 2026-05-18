package com.kuaishou.intelligentanalysisplatform.application.compute;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.enums.JoinType;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetStatDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.JoinCondition;
import org.springframework.stereotype.Service;

/**
 * 内存 Hash Join 服务，支持 INNER / LEFT / RIGHT / FULL 四种连接类型。
 *
 * <p>适用场景：两个数据集已加载至内存（来自上游节点的 DatasetDTO），
 * 无法或不需要下推到数据库时，由应用层完成 JOIN 计算。
 *
 * <p>算法（经典两阶段 Hash Join）：
 * <ol>
 *   <li><b>Build 阶段</b>：以左表为 Build Side，按 Join Key 构建 HashMap，
 *       Key 为多字段组合的 List&lt;Object&gt;，Value 为匹配的左表行列表。</li>
 *   <li><b>Probe 阶段</b>：遍历右表每行，从 HashMap 中查找匹配的左表行并合并输出。</li>
 *   <li><b>补充阶段</b>：LEFT / FULL JOIN 补充左表未匹配行；RIGHT / FULL JOIN 补充右表无匹配行。</li>
 * </ol>
 *
 * <p>安全保护：执行前通过 {@link #estimateResultRows} 估算结果行数，
 * 超过 rowLimit 时直接拒绝，防止内存溢出（默认上限 500,000 行）。
 *
 * <p>字段冲突处理：左右表字段同名时，右表字段以 {@code "right_"} 为前缀，
 * Schema 同步添加别名，保证列名唯一性。
 */
@Service
public class InMemoryHashJoinService {

    /** 默认最大行数上限（50 万行）；调用方可通过 rowLimit 参数覆盖 */
    private static final int DEFAULT_ROW_LIMIT = 500_000;
    /** INNER JOIN 选择率经验值（0.3 = 30%），用于估算 JOIN 结果行数，非精确值 */
    private static final double SELECTIVITY = 0.3;

    /**
     * 执行内存 Hash JOIN。
     *
     * @param left          左表数据集
     * @param right         右表数据集
     * @param joinType      JOIN 类型（INNER / LEFT / RIGHT / FULL）
     * @param conditions    JOIN 条件列表，支持多字段联合键
     * @param selectColumns 输出字段白名单，null 或空则输出全部字段
     * @param rowLimit      结果行数上限，超过则抛出异常（传 0 或负数使用默认值 500,000）
     * @return 合并后的数据集，包含完整的 Schema 信息和统计信息
     * @throws BaseBusinessException joinType 或 conditions 为 null，或估算行数超限时抛出
     */
    public DatasetDTO join(DatasetDTO left, DatasetDTO right,
                           JoinType joinType, List<JoinCondition> conditions,
                           List<String> selectColumns, int rowLimit) {
        if (joinType == null) {
            throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "joinType must not be null");
        }
        if (conditions == null || conditions.isEmpty()) {
            throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT,
                    "JOIN conditions (on) must not be empty");
        }
        List<Map<String, Object>> leftRows = safeRows(left);
        List<Map<String, Object>> rightRows = safeRows(right);

        // 1. 估算结果行数，超限拒绝
        long estimatedRows = estimateResultRows(leftRows.size(), rightRows.size(), joinType);
        if (estimatedRows > rowLimit) {
            throw new BaseBusinessException(ErrorCode.JOIN_ROW_LIMIT_EXCEEDED,
                    "estimated JOIN result exceeds row limit " + rowLimit +
                    " (estimated=" + estimatedRows + "), consider filtering data first");
        }

        // 2. Build Phase：左表按 JoinKey 构建 HashMap
        Map<List<Object>, List<Map<String, Object>>> buildMap = buildHashMap(leftRows, conditions);

        // 3. Probe Phase：遍历右表匹配
        List<Map<String, Object>> resultRows = new ArrayList<>();
        // 记录右表已匹配的 key（用于 RIGHT/FULL 补充未匹配左行）
        Set<List<Object>> matchedLeftKeys = new HashSet<>();

        for (Map<String, Object> rightRow : rightRows) {
            List<Object> key = extractRightKey(rightRow, conditions);
            List<Map<String, Object>> leftMatches = buildMap.getOrDefault(key, List.of());

            if (!leftMatches.isEmpty()) {
                for (Map<String, Object> leftRow : leftMatches) {
                    resultRows.add(mergeRow(leftRow, rightRow, selectColumns));
                }
                matchedLeftKeys.add(key);
            } else if (joinType == JoinType.RIGHT || joinType == JoinType.FULL) {
                // RIGHT/FULL JOIN：右表无匹配行，左侧字段填 null
                resultRows.add(mergeRow(null, rightRow, selectColumns));
            }
        }

        // 4. LEFT/FULL JOIN：补充左表中未匹配的行
        if (joinType == JoinType.LEFT || joinType == JoinType.FULL) {
            for (Map.Entry<List<Object>, List<Map<String, Object>>> entry : buildMap.entrySet()) {
                if (!matchedLeftKeys.contains(entry.getKey())) {
                    for (Map<String, Object> leftRow : entry.getValue()) {
                        resultRows.add(mergeRow(leftRow, null, selectColumns));
                    }
                }
            }
        }

        return buildResultDataset(resultRows, left, right, selectColumns);
    }

    /**
     * 估算 JOIN 结果行数，用于提前拒绝可能导致 OOM 的操作。
     *
     * <p>INNER JOIN：left × right × SELECTIVITY（启发式选择率 0.3）。
     * 外连接：至少保留较大一侧的行数（因未匹配行也会输出）。
     */
    private long estimateResultRows(int leftSize, int rightSize, JoinType joinType) {
        if (joinType == JoinType.INNER) {
            return (long) ((long) leftSize * rightSize * SELECTIVITY);
        }
        // LEFT/RIGHT/FULL：至少保留较大的一侧
        long innerEstimate = (long) ((long) leftSize * rightSize * SELECTIVITY);
        return Math.max(innerEstimate, Math.max(leftSize, rightSize));
    }

    private Map<List<Object>, List<Map<String, Object>>> buildHashMap(
            List<Map<String, Object>> rows, List<JoinCondition> conditions) {
        Map<List<Object>, List<Map<String, Object>>> hashMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            List<Object> key = extractLeftKey(row, conditions);
            hashMap.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }
        return hashMap;
    }

    private List<Object> extractLeftKey(Map<String, Object> row, List<JoinCondition> conditions) {
        return conditions.stream()
                .map(c -> row.get(c.getLeftField()))
                .collect(Collectors.toList());
    }

    private List<Object> extractRightKey(Map<String, Object> row, List<JoinCondition> conditions) {
        return conditions.stream()
                .map(c -> row.get(c.getRightField()))
                .collect(Collectors.toList());
    }

    /**
     * 合并左右行为一行输出，并按 selectColumns 过滤字段。
     *
     * <p>字段冲突：右表与左表同名字段添加 "right_" 前缀，保证合并后列名唯一。
     * 外连接的空侧（leftRow=null 或 rightRow=null）跳过对应字段填充（自动为 null）。
     *
     * @param leftRow       左表行（RIGHT JOIN 右侧无匹配时为 null）
     * @param rightRow      右表行（LEFT JOIN 左侧无匹配时为 null）
     * @param selectColumns 输出字段白名单（null 则输出所有字段）
     */
    private Map<String, Object> mergeRow(Map<String, Object> leftRow,
                                         Map<String, Object> rightRow,
                                         List<String> selectColumns) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (leftRow != null) {
            merged.putAll(leftRow);
        }
        if (rightRow != null) {
            // 右表字段若与左表重名则加前缀区分
            for (Map.Entry<String, Object> entry : rightRow.entrySet()) {
                String key = merged.containsKey(entry.getKey())
                        ? "right_" + entry.getKey()
                        : entry.getKey();
                merged.put(key, entry.getValue());
            }
        }
        if (selectColumns != null && !selectColumns.isEmpty()) {
            Map<String, Object> selected = new LinkedHashMap<>();
            for (String col : selectColumns) {
                selected.put(col, merged.get(col));
            }
            return selected;
        }
        return merged;
    }

    private DatasetDTO buildResultDataset(List<Map<String, Object>> resultRows,
                                          DatasetDTO left, DatasetDTO right,
                                          List<String> selectColumns) {
        List<FieldSchemaDTO> mergedFields = mergeSchema(left, right, selectColumns);

        DatasetStatDTO stat = DatasetStatDTO.builder()
                .rowCount(resultRows.size())
                .returnedRowCount(resultRows.size())
                .truncated(false)
                .extensions(Map.of(
                        "leftInputRowCount", safeRows(left).size(),
                        "rightInputRowCount", safeRows(right).size(),
                        "outputRowCount", resultRows.size()))
                .build();

        return DatasetDTO.builder()
                .rows(resultRows)
                .schema(DatasetSchemaDTO.builder()
                        .fields(mergedFields)
                        .metrics(List.of())
                        .dimensions(List.of())
                        .timeFields(List.of())
                        .build())
                .stat(stat)
                .build();
    }

    private List<FieldSchemaDTO> mergeSchema(DatasetDTO left, DatasetDTO right,
                                             List<String> selectColumns) {
        Set<String> fieldNames = new LinkedHashSet<>();
        List<FieldSchemaDTO> allFields = new ArrayList<>();

        if (left != null && left.getSchema() != null && left.getSchema().getFields() != null) {
            for (FieldSchemaDTO f : left.getSchema().getFields()) {
                if (fieldNames.add(f.getName())) {
                    allFields.add(f);
                }
            }
        }
        if (right != null && right.getSchema() != null && right.getSchema().getFields() != null) {
            for (FieldSchemaDTO f : right.getSchema().getFields()) {
                String name = fieldNames.contains(f.getName()) ? "right_" + f.getName() : f.getName();
                if (fieldNames.add(name)) {
                    allFields.add(name.equals(f.getName()) ? f : FieldSchemaDTO.builder()
                            .fieldId("right_" + f.getFieldId())
                            .name(name)
                            .displayName("右表." + f.getDisplayName())
                            .valueType(f.getValueType())
                            .nullable(true)
                            .semanticType(f.getSemanticType())
                            .build());
                }
            }
        }

        if (selectColumns == null || selectColumns.isEmpty()) {
            return allFields;
        }
        Set<String> selected = new LinkedHashSet<>(selectColumns);
        return allFields.stream()
                .filter(f -> selected.contains(f.getName()))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> safeRows(DatasetDTO dataset) {
        if (dataset == null || dataset.getRows() == null) {
            return List.of();
        }
        return dataset.getRows();
    }
}
