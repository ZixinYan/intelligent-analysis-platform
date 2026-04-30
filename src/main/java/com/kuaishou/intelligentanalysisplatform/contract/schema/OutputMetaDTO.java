package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OutputMetaDTO {
    private String sourceNodeId;
    private String generatedAt;
    private Boolean downloadable;
    private Boolean partial;
    private Integer totalRows;
    private Integer returnedRows;
    private String truncationStrategy;
}
