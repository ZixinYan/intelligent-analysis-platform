package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableOutputDTO {
    private String title;
    private List<TableColumnDTO> columns;
    private List<Map<String, Object>> rows;
    private TableOptionDTO option;
    private OutputMetaDTO meta;
}
