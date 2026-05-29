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
public class ChartSeriesDTO {
    private String name;
    private String stack;
    private List<Object> data;
    private String yAxis;
}
