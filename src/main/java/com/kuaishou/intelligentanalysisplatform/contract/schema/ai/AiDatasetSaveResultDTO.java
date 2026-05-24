package com.kuaishou.intelligentanalysisplatform.contract.schema.ai;

import com.kuaishou.intelligentanalysisplatform.contract.schema.SavedDatasetSummaryDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDatasetSaveResultDTO {
    private String datasetId;
    private SavedDatasetSummaryDTO dataset;
}
