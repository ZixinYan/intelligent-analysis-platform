package com.kuaishou.intelligentanalysisplatform.common.error;

import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ErrorInfoDTO {
    private String code;
    private String message;
    private String detail;
    private String requestId;
    private String nodeId;
    private Boolean retryable;
    private Map<String, Object> extra;
}
