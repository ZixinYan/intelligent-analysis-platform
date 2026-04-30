package com.kuaishou.intelligentanalysisplatform.application.node.output;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetStatDTO;
import org.springframework.stereotype.Component;

@Component
public class DatasetTruncator {
    public static final int DEFAULT_CHART_MAX_ROWS = 10000;
    public static final int DEFAULT_TABLE_MAX_ROWS = 5000;
    public static final String STRATEGY_HEAD_N = "HEAD_N";

    public TruncateResult truncateForChart(DatasetDTO dataset) {
        return truncate(dataset, DEFAULT_CHART_MAX_ROWS);
    }

    public TruncateResult truncateForTable(DatasetDTO dataset) {
        return truncate(dataset, DEFAULT_TABLE_MAX_ROWS);
    }

    public TruncateResult truncate(DatasetDTO dataset, int maxRows) {
        List<Map<String, Object>> rows = dataset == null ? null : dataset.getRows();
        int totalRows = rows == null ? 0 : rows.size();
        if (rows == null || totalRows <= maxRows) {
            return new TruncateResult(enrich(dataset, totalRows, totalRows, false), totalRows, totalRows, false, null);
        }
        List<Map<String, Object>> truncatedRows = rows.subList(0, maxRows);
        DatasetDTO truncatedDataset = DatasetDTO.builder()
                .schema(dataset.getSchema())
                .rows(truncatedRows)
                .page(dataset.getPage())
                .stat(buildStat(totalRows, maxRows, true))
                .build();
        return new TruncateResult(truncatedDataset, totalRows, maxRows, true, STRATEGY_HEAD_N);
    }

    private DatasetDTO enrich(DatasetDTO dataset, int totalRows, int returnedRows, boolean truncated) {
        if (dataset == null) {
            return DatasetDTO.builder()
                    .rows(List.of())
                    .stat(buildStat(totalRows, returnedRows, truncated))
                    .build();
        }
        return DatasetDTO.builder()
                .schema(dataset.getSchema())
                .rows(dataset.getRows())
                .page(dataset.getPage())
                .stat(buildStat(totalRows, returnedRows, truncated))
                .build();
    }

    private DatasetStatDTO buildStat(int totalRows, int returnedRows, boolean truncated) {
        return DatasetStatDTO.builder()
                .rowCount(totalRows)
                .returnedRowCount(returnedRows)
                .truncated(truncated)
                .build();
    }

    public record TruncateResult(DatasetDTO dataset, int totalRows, int returnedRows, boolean partial,
                                 String truncationStrategy) {
    }
}
