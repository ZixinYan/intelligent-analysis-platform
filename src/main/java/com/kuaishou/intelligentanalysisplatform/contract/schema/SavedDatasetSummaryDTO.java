package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SavedDatasetSummaryDTO {
    private String datasetId;
    private String name;
    private String description;
    private String tenantId;
    private String createdBy;
    private DatasetSchemaDTO schema;
    private DatasetStatDTO stat;
    private String sourceWorkflowId;
    private String sourceNodeId;
    private Long createdAt;
    private Long updatedAt;
}
