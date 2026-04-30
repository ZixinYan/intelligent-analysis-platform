package com.kuaishou.intelligentanalysisplatform.common.response;

import java.util.UUID;

import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorInfoDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseBusinessException.class)
    public ResponseEntity<ApiResponse<ErrorInfoDTO>> handleBusiness(BaseBusinessException ex, HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        ErrorInfoDTO body = ErrorInfoDTO.builder()
                .code(ex.getErrorCode().getCode())
                .message(ex.getMessage())
                .detail(ex.getDetail())
                .requestId(request.getMethod() + " " + request.getRequestURI())
                .nodeId(ex.getNodeId())
                .retryable(ex.isRetryable())
                .build();
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                .body(ApiResponse.failure(ex.getErrorCode().getCode(), ex.getMessage(), body, traceId));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<ErrorInfoDTO>> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        ErrorInfoDTO body = ErrorInfoDTO.builder()
                .code(ErrorCode.UNAUTHORIZED.getCode())
                .message("unauthorized")
                .detail(ex.getMessage())
                .requestId(request.getMethod() + " " + request.getRequestURI())
                .retryable(false)
                .build();
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.UNAUTHORIZED.getCode(), "unauthorized", body, traceId));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ErrorInfoDTO>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        ErrorInfoDTO body = ErrorInfoDTO.builder()
                .code(ErrorCode.VALIDATION_FAILED.getCode())
                .message("validation failed")
                .detail(ex.getMessage())
                .requestId(request.getMethod() + " " + request.getRequestURI())
                .retryable(false)
                .build();
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.VALIDATION_FAILED.getCode(), "validation failed", body, traceId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ErrorInfoDTO>> handleUnexpected(Exception ex, HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        ErrorInfoDTO body = ErrorInfoDTO.builder()
                .code(ErrorCode.INTERNAL_ERROR.getCode())
                .message("internal error")
                .detail(ex.getMessage())
                .requestId(request.getMethod() + " " + request.getRequestURI())
                .retryable(false)
                .build();
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.INTERNAL_ERROR.getCode(), "internal error", body, traceId));
    }

    private String resolveTraceId(HttpServletRequest request) {
        String header = request.getHeader("X-Trace-Id");
        if (header != null && !header.isBlank()) {
            return header;
        }
        return UUID.randomUUID().toString();
    }
}
