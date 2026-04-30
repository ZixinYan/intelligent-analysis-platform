package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class TableOutputNodeConfigDTO extends BaseNodeConfigDTO {
    private VariableRefDTO datasetRef;
    private List<TableColumnMappingDTO> columns;
    private TableOptionDTO option;
}
