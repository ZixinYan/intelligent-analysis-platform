package com.kuaishou.intelligentanalysisplatform.infra.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AOP aspect that intercepts all REST controller methods to log API call audits.
 * Logs method entry with request parameters. Errors are logged separately by GlobalExceptionHandler.
 * Uses the dedicated "API_AUDIT" logger for clear separation from other logs.
 */
@Aspect
@Component
public class ApiLogAspect {

    private static final Logger API_AUDIT = LoggerFactory.getLogger("API_AUDIT");

    private final ObjectMapper objectMapper;
    private final HttpServletRequest httpServletRequest;

    public ApiLogAspect(ObjectMapper objectMapper, HttpServletRequest httpServletRequest) {
        this.objectMapper = objectMapper;
        this.httpServletRequest = httpServletRequest;
    }

    @Around("execution(* com.kuaishou.intelligentanalysisplatform.interfaces.rest..*(..))")
    public Object logApi(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();

        // Build request parameters map
        Map<String, Object> params = new LinkedHashMap<>();
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < parameters.length; i++) {
            String paramName = parameters[i].getName();
            Object argValue = args[i];
            if (argValue instanceof HttpServletRequest || argValue instanceof jakarta.servlet.http.HttpServletResponse) {
                continue;
            }
            params.put(paramName, argValue);
        }

        // Log request
        try {
            API_AUDIT.info(objectMapper.writeValueAsString(buildRequestLog(className, methodName, params)));
        } catch (JsonProcessingException e) {
            API_AUDIT.info("{\"event\":\"api_request\",\"class\":\"" + className + "\",\"method\":\"" + methodName + "\"}");
        }

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            logApiError(className, methodName, e, elapsed);
            throw e;
        }

        return result;
    }

    private Map<String, Object> buildRequestLog(String className, String methodName, Map<String, Object> params) {
        Map<String, Object> log = new LinkedHashMap<>();
        log.put("event", "api_request");
        log.put("class", className);
        log.put("method", methodName);
        log.put("params", params);
        return log;
    }

    private void logApiError(String className, String methodName, Exception e, long elapsed) {
        Map<String, Object> errorLog = new LinkedHashMap<>();
        errorLog.put("event", "api_error");
        errorLog.put("class", className);
        errorLog.put("method", methodName);
        errorLog.put("elapsedMs", elapsed);
        errorLog.put("error", e.getClass().getSimpleName());
        errorLog.put("errorMessage", e.getMessage());
        if (e instanceof com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException be) {
            errorLog.put("errorCode", be.getErrorCode().getCode());
            if (be.getDetail() != null) {
                errorLog.put("detail", be.getDetail());
            }
        }
        try {
            API_AUDIT.warn(objectMapper.writeValueAsString(errorLog));
        } catch (JsonProcessingException ex) {
            API_AUDIT.warn("api_error: class={}, method={}, elapsedMs={}, error={}",
                    className, methodName, elapsed, e.getClass().getSimpleName());
        }
    }
}
