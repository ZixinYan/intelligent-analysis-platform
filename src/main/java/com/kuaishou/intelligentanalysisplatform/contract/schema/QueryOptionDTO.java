package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryOptionDTO {
    private Integer timeoutMs;
    private Integer limit;
    private Integer offset;
    private Integer pageSize;
    private String cursor;
    private Boolean useCache;
    private Integer cacheTtlSeconds;
    private Boolean readOnly;
    private Boolean asyncPreferred;
    /** 流式分块大小（行数），默认 500；仅 streaming=true 时生效 */
    private Integer chunkSize;
    /** 是否启用 SSE 流式推送 */
    private Boolean streaming;
}
