package com.kuaishou.intelligentanalysisplatform.contract.schema.ai;

import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDatasetSaveRequestDTO {
    @NotBlank
    private String name;
    private String description;
    private DatasetDTO dataset;
    private String sourceWorkflowId;
    private String sourceNodeId;
}
