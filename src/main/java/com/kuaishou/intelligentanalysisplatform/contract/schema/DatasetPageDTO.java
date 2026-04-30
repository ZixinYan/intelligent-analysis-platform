package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DatasetPageDTO {
    private Integer pageSize;
    private Integer currentPage;
    private Long total;
    private String nextCursor;
}
