package com.kuaishou.intelligentanalysisplatform.contract.schema;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ChartType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChartOutputDTO {
    private String title;
    private ChartType chartType;
    private ChartDataDTO data;
    private ChartOptionDTO option;
    private OutputMetaDTO meta;
}
