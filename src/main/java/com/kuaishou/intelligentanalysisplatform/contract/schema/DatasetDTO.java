package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DatasetDTO {
    private DatasetSchemaDTO schema;
    private List<Map<String, Object>> rows;
    private DatasetPageDTO page;
    private DatasetStatDTO stat;
}
