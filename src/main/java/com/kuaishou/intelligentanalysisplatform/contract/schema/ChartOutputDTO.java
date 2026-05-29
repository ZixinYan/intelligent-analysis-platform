package com.kuaishou.intelligentanalysisplatform.contract.schema;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ChartType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChartOutputDTO {
    private String title;
    private ChartType chartType;
    private ChartDataDTO data;
    private ChartOptionDTO option;
    private OutputMetaDTO meta;
}
