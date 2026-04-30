package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChartMappingDTO {
    private String categoryField;
    private List<String> seriesFields;
    private String stackField;
}
