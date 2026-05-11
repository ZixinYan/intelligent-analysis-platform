package com.kuaishou.intelligentanalysisplatform.domain.dataset;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetSchemaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetStatDTO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SavedDataset {
    private String datasetId;
    private String tenantId;
    private String name;
    private String description;
    private String createdBy;
    private DatasetSchemaDTO schema;
    private DatasetStatDTO stat;
    private List<Map<String, Object>> rows;
    private String sourceWorkflowId;
    private String sourceNodeId;
    private Long createdAt;
    private Long updatedAt;
}
