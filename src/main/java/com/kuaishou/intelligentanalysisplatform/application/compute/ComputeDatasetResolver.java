package com.kuaishou.intelligentanalysisplatform.application.compute;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.enums.FieldSemanticType;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ResultKind;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ValueType;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetStatDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.VariableRefDTO;
import org.springframework.stereotype.Component;

@Component
public class ComputeDatasetResolver {

    /**
     * 解析单个数据集：优先使用 datasetRef 指定的上游节点，否则取第一个 DATASET 类型的上游结果。
     */
    public DatasetDTO resolve(VariableRefDTO datasetRef, Map<String, StandardResultDTO> upstreamResults) {
        if (datasetRef != null) {
            StandardResultDTO standardResult = upstreamResults == null ? null : upstreamResults.get(datasetRef.getSourceNodeId());
            if (standardResult != null && standardResult.getDataset() != null) {
                return standardResult.getDataset();
            }
        }
        if (upstreamResults != null) {
            for (StandardResultDTO result : upstreamResults.values()) {
                if (result != null && result.getKind() == ResultKind.DATASET && result.getDataset() != null) {
                    return result.getDataset();
                }
            }
        }
        throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "dataset input is required");
    }

    /**
     * 解析所有上游数据集列表。
     * 若指定了 datasetRef 则只返回该节点的数据集；否则返回所有 DATASET 类型的上游结果。
     */
    public List<DatasetDTO> resolveAll(VariableRefDTO datasetRef, Map<String, StandardResultDTO> upstreamResults) {
        if (datasetRef != null) {
            StandardResultDTO standardResult = upstreamResults == null ? null : upstreamResults.get(datasetRef.getSourceNodeId());
            if (standardResult != null && standardResult.getDataset() != null) {
                return List.of(standardResult.getDataset());
            }
        }
        if (upstreamResults == null || upstreamResults.isEmpty()) {
            return List.of();
        }
        return upstreamResults.values().stream()
                .filter(r -> r != null && r.getKind() == ResultKind.DATASET && r.getDataset() != null)
                .map(StandardResultDTO::getDataset)
                .collect(Collectors.toList());
    }

    /**
     * 将多个数据集合并为一个（UNION ALL 语义）。
     * 合并后的 schema 取所有数据集字段的并集，缺失字段填 null。
     * 若只有一个数据集，直接返回，不做额外处理。
     */
    public DatasetDTO mergeDatasets(List<DatasetDTO> datasets) {
        if (datasets == null || datasets.isEmpty()) {
            throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, "dataset input is required");
        }
        if (datasets.size() == 1) {
            return datasets.get(0);
        }

        // 收集所有字段名（保持顺序，去重）
        Map<String, FieldSchemaDTO> fieldMap = new LinkedHashMap<>();
        for (DatasetDTO ds : datasets) {
            if (ds.getSchema() != null && ds.getSchema().getFields() != null) {
                for (FieldSchemaDTO f : ds.getSchema().getFields()) {
                    String key = f.getName() != null ? f.getName() : f.getFieldId();
                    if (key != null && !fieldMap.containsKey(key)) {
                        fieldMap.put(key, f);
                    }
                }
            }
        }
        // 若所有数据集都没有 schema，从 rows 推断字段名
        if (fieldMap.isEmpty()) {
            for (DatasetDTO ds : datasets) {
                if (ds.getRows() != null) {
                    for (Map<String, Object> row : ds.getRows()) {
                        for (String key : row.keySet()) {
                            if (!fieldMap.containsKey(key)) {
                                fieldMap.put(key, FieldSchemaDTO.builder()
                                        .fieldId(key).name(key).displayName(key)
                                        .valueType(ValueType.STRING).nullable(true)
                                        .semanticType(FieldSemanticType.DIMENSION)
                                        .build());
                            }
                        }
                        break; // 只看第一行即可推断字段
                    }
                }
            }
        }

        // 合并所有数据集的行（UNION ALL，缺失字段填 null）
        List<Map<String, Object>> mergedRows = new ArrayList<>();
        for (DatasetDTO ds : datasets) {
            if (ds.getRows() != null) {
                for (Map<String, Object> row : ds.getRows()) {
                    Map<String, Object> mergedRow = new LinkedHashMap<>();
                    for (String fieldName : fieldMap.keySet()) {
                        mergedRow.put(fieldName, row.getOrDefault(fieldName, null));
                    }
                    mergedRows.add(mergedRow);
                }
            }
        }

        int totalRows = mergedRows.size();
        DatasetStatDTO stat = DatasetStatDTO.builder()
                .rowCount(totalRows)
                .returnedRowCount(totalRows)
                .truncated(false)
                .extensions(Map.of("mergedSourceCount", datasets.size()))
                .build();

        return DatasetDTO.builder()
                .schema(DatasetSchemaDTO.builder()
                        .fields(new ArrayList<>(fieldMap.values()))
                        .metrics(List.of())
                        .dimensions(List.of())
                        .timeFields(List.of())
                        .build())
                .rows(mergedRows)
                .stat(stat)
                .build();
    }
}
