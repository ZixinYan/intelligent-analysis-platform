package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChartDataDTO {
    private List<String> categories;
    private List<ChartSeriesDTO> series;
}
