package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DatasetSchemaDTO {
    private List<FieldSchemaDTO> fields;
    private List<MetricFieldDTO> metrics;
    private List<DimensionFieldDTO> dimensions;
    private List<TimeFieldDTO> timeFields;
}
