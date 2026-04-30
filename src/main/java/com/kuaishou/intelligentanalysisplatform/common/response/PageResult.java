package com.kuaishou.intelligentanalysisplatform.common.response;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PageResult<T> {
    private List<T> items;
    private long total;
    private int page;
    private int pageSize;
}
