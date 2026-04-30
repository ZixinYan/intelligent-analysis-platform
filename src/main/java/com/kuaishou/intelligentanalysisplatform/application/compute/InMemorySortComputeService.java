package com.kuaishou.intelligentanalysisplatform.application.compute;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SortNodeConfigDTO;
import org.springframework.stereotype.Service;

@Service
public class InMemorySortComputeService {
    public DatasetDTO compute(SortNodeConfigDTO config, DatasetDTO input) {
        List<Map<String, Object>> rows = input == null || input.getRows() == null ? List.of() : input.getRows();
        List<Map<String, Object>> sorted = rows.stream().map(java.util.LinkedHashMap::new).collect(Collectors.toList());
        if (ComputeSupport.sortComparator(config.getSortFields()) != null) {
            sorted = sorted.stream().sorted(ComputeSupport.sortComparator(config.getSortFields())).collect(Collectors.toList());
        }
        if (config.getLimit() != null && config.getLimit() > 0 && sorted.size() > config.getLimit()) {
            sorted = sorted.subList(0, config.getLimit());
        }
        return ComputeSupport.dataset(sorted, input == null || input.getSchema() == null ? List.of() : input.getSchema().getFields(), Map.of("outputRowCount", sorted.size()));
    }
}
