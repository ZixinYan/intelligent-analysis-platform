package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TableColumnMappingDTO {
    private String sourceField;
    private String targetField;
    private String label;
}
