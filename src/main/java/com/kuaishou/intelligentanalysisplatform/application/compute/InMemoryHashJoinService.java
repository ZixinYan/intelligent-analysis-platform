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

@Service
public class InMemoryHashJoinService {

    private static final int DEFAULT_ROW_LIMIT = 500_000;
    private static final double SELECTIVITY = 0.3;

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
