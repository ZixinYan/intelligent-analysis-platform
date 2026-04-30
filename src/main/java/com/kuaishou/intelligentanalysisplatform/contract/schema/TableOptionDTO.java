package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TableOptionDTO {
    private Boolean pageable;
    private Boolean downloadable;
    private Integer pageSize;
}
