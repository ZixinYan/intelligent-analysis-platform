package com.kuaishou.intelligentanalysisplatform.common.error;

import lombok.Getter;

@Getter
public class BaseBusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    private final String detail;
    private final String nodeId;
    private final boolean retryable;

    public BaseBusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, null, null, false);
    }

    public BaseBusinessException(ErrorCode errorCode, String message, String detail, String nodeId, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.detail = detail;
        this.nodeId = nodeId;
        this.retryable = retryable;
    }
}
