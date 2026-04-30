package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChartSeriesDTO {
    private String name;
    private String stack;
    private List<Object> data;
    private String yAxis;
}
