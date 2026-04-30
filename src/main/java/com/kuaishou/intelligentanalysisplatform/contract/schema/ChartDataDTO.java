package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChartDataDTO {
    private List<String> categories;
    private List<ChartSeriesDTO> series;
}
