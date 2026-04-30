package com.kuaishou.intelligentanalysisplatform.application.compute;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.PivotNodeConfigDTO;
import org.springframework.stereotype.Service;

@Service
public class InMemoryPivotComputeService {
    public DatasetDTO compute(PivotNodeConfigDTO config, DatasetDTO input) {
        List<Map<String, Object>> rows = input == null || input.getRows() == null ? List.of() : input.getRows();
        Map<List<Object>, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            List<Object> key = config.getRowFields() == null ? List.of("__all__") : config.getRowFields().stream().map(row::get).collect(Collectors.toList());
            Map<String, Object> pivotRow = grouped.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
            if (config.getRowFields() != null) {
                for (String field : config.getRowFields()) {
                    pivotRow.put(field, row.get(field));
                }
            }
            pivotRow.put(String.valueOf(row.get(config.getColumnField())), row.get(config.getValueField()));
        }
        List<FieldSchemaDTO> fields = new ArrayList<>();
        for (String rowField : config.getRowFields() == null ? List.<String>of() : config.getRowFields()) {
            fields.add(ComputeSupport.dimensionField(rowField));
        }
        return ComputeSupport.dataset(new ArrayList<>(grouped.values()), fields, Map.of("outputRowCount", grouped.size()));
    }
}
