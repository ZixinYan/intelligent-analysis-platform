package com.kuaishou.intelligentanalysisplatform.infra.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowRunResultDTO;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AOP aspect that intercepts task execution methods to log execution audits.
 * Covers workflow execution, node execution, async task submission, and query execution.
 * Uses the dedicated "TASK_EXECUTION" logger for clear separation.
 */
@Aspect
@Component
public class TaskExecutionLogAspect {

    private static final Logger TASK_EXECUTION = LoggerFactory.getLogger("TASK_EXECUTION");

    private final ObjectMapper objectMapper;

    public TaskExecutionLogAspect(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Log workflow execution in DefaultSyncExecutionService and WorkflowDagExecutor.
     */
    @Around("execution(* com.kuaishou.intelligentanalysisplatform.application.impl.DefaultSyncExecutionService.runWorkflow(..)) || " +
            "execution(* com.kuaishou.intelligentanalysisplatform.application.impl.WorkflowDagExecutor.execute(..))")
    public Object logWorkflowExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();

        logTaskEvent("workflow_execution_start", className, methodName, null, start);

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            logTaskError("workflow_execution_failed", className, methodName, e, elapsed);
            throw e;
        }

        long elapsed = System.currentTimeMillis() - start;
        String status = null;
        int nodeCount = 0;
        if (result instanceof WorkflowRunResultDTO wfResult) {
            status = wfResult.getStatus() != null ? wfResult.getStatus().name() : null;
            nodeCount = wfResult.getNodeResults() != null ? wfResult.getNodeResults().size() : 0;
        }

        logTaskEvent("workflow_execution_completed", className, methodName,
                Map.of("status", status != null ? status : "UNKNOWN",
                        "nodeCount", nodeCount), elapsed);
        return result;
    }

    /**
     * Log node execution in NodeExecuteDispatcher.
     */
    @Around("execution(* com.kuaishou.intelligentanalysisplatform.application.node.NodeExecuteDispatcher.dispatch(..))")
    public Object logNodeExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();

        Object[] args = joinPoint.getArgs();
        String nodeId = null;
        String nodeType = null;
        if (args.length > 0 && args[0] instanceof com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowNodeDTO node) {
            nodeId = node.getNodeId();
            nodeType = node.getNodeType();
        }

        logTaskEvent("node_execution_start", className, "dispatch",
                Map.of("nodeId", nodeId != null ? nodeId : "unknown",
                        "nodeType", nodeType != null ? nodeType : "unknown"), start);

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            logTaskError("node_execution_failed", className, "dispatch", e, elapsed);
            throw e;
        }

        long elapsed = System.currentTimeMillis() - start;
        String status = null;
        if (result instanceof NodeResultDTO nodeResult) {
            status = nodeResult.getStatus() != null ? nodeResult.getStatus().name() : null;
        }

        logTaskEvent("node_execution_completed", className, "dispatch",
                Map.of("nodeId", nodeId != null ? nodeId : "unknown",
                        "nodeType", nodeType != null ? nodeType : "unknown",
                        "status", status != null ? status : "UNKNOWN"), elapsed);
        return result;
    }

    /**
     * Log async task submission in DefaultAsyncExecutionService.
     */
    @Around("execution(* com.kuaishou.intelligentanalysisplatform.application.impl.DefaultAsyncExecutionService.submitWorkflow(..)) || " +
            "execution(* com.kuaishou.intelligentanalysisplatform.application.impl.DefaultAsyncExecutionService.submitNode(..))")
    public Object logAsyncTaskSubmission(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();

        logTaskEvent("async_task_submission_start", className, methodName, null, start);

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            logTaskError("async_task_submission_failed", className, methodName, e, elapsed);
            throw e;
        }

        long elapsed = System.currentTimeMillis() - start;
        logTaskEvent("async_task_submission_completed", className, methodName, null, elapsed);
        return result;
    }

    private void logTaskEvent(String event, String className, String method,
                              Map<String, Object> extraInfo, long startTime) {
        Map<String, Object> log = new LinkedHashMap<>();
        log.put("event", event);
        log.put("class", className);
        log.put("method", method);
        if (extraInfo != null) {
            log.putAll(extraInfo);
        }
        try {
            TASK_EXECUTION.info(objectMapper.writeValueAsString(log));
        } catch (JsonProcessingException e) {
            TASK_EXECUTION.info("{\"event\":\"" + event + "\",\"class\":\"" + className + "\"}");
        }
    }

    private void logTaskError(String event, String className, String method, Exception e, long elapsed) {
        Map<String, Object> errorLog = new LinkedHashMap<>();
        errorLog.put("event", event);
        errorLog.put("class", className);
        errorLog.put("method", method);
        errorLog.put("elapsedMs", elapsed);
        errorLog.put("error", e.getClass().getSimpleName());
        errorLog.put("errorMessage", e.getMessage());
        try {
            TASK_EXECUTION.warn(objectMapper.writeValueAsString(errorLog));
        } catch (JsonProcessingException ex) {
            TASK_EXECUTION.warn("task_error: event={}, class={}, elapsedMs={}, error={}",
                    event, className, elapsed, e.getClass().getSimpleName());
        }
    }
}
