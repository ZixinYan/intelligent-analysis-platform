package com.kuaishou.intelligentanalysisplatform.application.node.output;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ChartType;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ChartDataDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ChartMappingDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ChartOptionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ChartOutputDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ChartSeriesDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.FieldSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.OutputMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.TableColumnDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.TableColumnMappingDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.TableOptionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.TableOutputDTO;
import org.springframework.stereotype.Component;

@Component
public class OutputProtocolAssembler {

    public ChartOutputDTO assembleChart(DatasetDTO dataset, ChartMappingDTO mapping, ChartType chartType,
                                        ChartOptionDTO option, String sourceNodeId) {
        List<Map<String, Object>> rows = safeRows(dataset);
        List<String> categories = rows.stream()
                .map(row -> stringify(row.get(mapping.getCategoryField())))
                .collect(Collectors.toList());
        List<ChartSeriesDTO> series = safeList(mapping.getSeriesFields()).stream()
                .map(field -> ChartSeriesDTO.builder()
                        .name(field)
                        .stack(mapping.getStackField())
                        .data(rows.stream().map(row -> row.get(field)).collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());
        return ChartOutputDTO.builder()
                .title(chartType == null ? null : chartType.name())
                .chartType(chartType)
                .data(ChartDataDTO.builder()
                        .categories(categories)
                        .series(series)
                        .build())
                .option(option)
                .meta(buildMeta(sourceNodeId, false, null, rows.size(), rows.size(), null))
                .build();
    }

    public TableOutputDTO assembleTable(DatasetDTO dataset, List<TableColumnMappingDTO> columnMappings,
                                        TableOptionDTO option, String sourceNodeId) {
        List<Map<String, Object>> rows = safeRows(dataset);
        return TableOutputDTO.builder()
                .title("TABLE")
                .columns(resolveColumns(dataset, columnMappings))
                .rows(rows)
                .option(option)
                .meta(buildMeta(sourceNodeId, false,
                        option == null ? null : option.getDownloadable(), rows.size(), rows.size(), null))
                .build();
    }

    public OutputMetaDTO buildMeta(String sourceNodeId, boolean partial, Boolean downloadable,
                                   Integer totalRows, Integer returnedRows, String truncationStrategy) {
        return OutputMetaDTO.builder()
                .sourceNodeId(sourceNodeId)
                .generatedAt(Instant.now().toString())
                .downloadable(downloadable)
                .partial(partial)
                .totalRows(totalRows)
                .returnedRows(returnedRows)
                .truncationStrategy(truncationStrategy)
                .build();
    }

    private List<TableColumnDTO> resolveColumns(DatasetDTO dataset, List<TableColumnMappingDTO> columnMappings) {
        if (columnMappings != null && !columnMappings.isEmpty()) {
            return columnMappings.stream()
                    .map(mapping -> TableColumnDTO.builder()
                            .field(mapping.getTargetField() == null || mapping.getTargetField().isBlank()
                                    ? mapping.getSourceField() : mapping.getTargetField())
                            .label(mapping.getLabel() == null || mapping.getLabel().isBlank()
                                    ? mapping.getSourceField() : mapping.getLabel())
                            .sortable(Boolean.TRUE)
                            .build())
                    .collect(Collectors.toList());
        }
        if (dataset == null || dataset.getSchema() == null || dataset.getSchema().getFields() == null) {
            return Collections.emptyList();
        }
        List<TableColumnDTO> columns = new ArrayList<>();
        for (FieldSchemaDTO field : dataset.getSchema().getFields()) {
            if (field == null || field.getFieldId() == null || field.getFieldId().isBlank()) {
                continue;
            }
            columns.add(TableColumnDTO.builder()
                    .field(field.getFieldId())
                    .label(field.getDisplayName() == null || field.getDisplayName().isBlank()
                            ? field.getName() == null || field.getName().isBlank() ? field.getFieldId() : field.getName()
                            : field.getDisplayName())
                    .valueType(field.getValueType())
                    .sortable(Boolean.TRUE)
                    .build());
        }
        return columns;
    }

    private List<Map<String, Object>> safeRows(DatasetDTO dataset) {
        if (dataset == null || dataset.getRows() == null) {
            return Collections.emptyList();
        }
        return dataset.getRows().stream().filter(Objects::nonNull).collect(Collectors.toList());
    }

    private List<String> safeList(List<String> fields) {
        return fields == null ? Collections.emptyList() : fields;
    }

    private String stringify(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
