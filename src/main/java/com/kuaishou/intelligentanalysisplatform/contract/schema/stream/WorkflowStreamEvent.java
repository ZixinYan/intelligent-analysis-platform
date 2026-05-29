package com.kuaishou.intelligentanalysisplatform.contract.schema.stream;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.common.error.ErrorInfoDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeRunMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;

/**
 * SSE 事件协议：定义工作流流式执行期间推送给前端的所有事件类型。
 */
public sealed interface WorkflowStreamEvent permits
        WorkflowStreamEvent.NodeStartEvent,
        WorkflowStreamEvent.NodeProgressEvent,
        WorkflowStreamEvent.NodeResultEvent,
        WorkflowStreamEvent.WorkflowDoneEvent,
        WorkflowStreamEvent.WorkflowErrorEvent,
        WorkflowStreamEvent.IterationStartedEvent,
        WorkflowStreamEvent.IterationNextEvent,
        WorkflowStreamEvent.IterationFinishedEvent {

    String eventType();

    String runId();

    /** 节点开始执行 */
    record NodeStartEvent(String runId, String nodeId, String nodeType, long startedAt)
            implements WorkflowStreamEvent {
        @Override
        public String eventType() { return "node_start"; }
    }

    /**
     * 数据集分块推送（大结果集按 chunkSize 分批推送）。
     * totalChunks 仅首包携带，后续包可为 null。
     */
    record NodeProgressEvent(String runId, String nodeId, int chunkIndex, Integer totalChunks,
                             List<Map<String, Object>> rows)
            implements WorkflowStreamEvent {
        @Override
        public String eventType() { return "node_progress"; }
    }

    /**
     * 节点执行完成（含最终结果摘要）。
     * 若数据已通过 NodeProgressEvent 分块推送，则 result.dataset.rows 为 null。
     * 失败时 error 字段携带错误信息，result 可为 null。
     */
    record NodeResultEvent(String runId, String nodeId, String status,
                           StandardResultDTO result, NodeRunMetaDTO meta,
                           ErrorInfoDTO error)
            implements WorkflowStreamEvent {
        @Override
        public String eventType() { return "node_result"; }
    }

    /** 工作流全部节点执行完成 */
    record WorkflowDoneEvent(String runId, String workflowId, String status, long elapsedMs)
            implements WorkflowStreamEvent {
        @Override
        public String eventType() { return "workflow_done"; }
    }

    /** 工作流级别错误（任意节点抛出未捕获异常或超时） */
    record WorkflowErrorEvent(String runId, String workflowId, ErrorInfoDTO error)
            implements WorkflowStreamEvent {
        @Override
        public String eventType() { return "workflow_error"; }
    }

    /** 迭代节点开始，携带输入数组总长度 */
    record IterationStartedEvent(String runId, String nodeId, String nodeType, int iterationLength)
            implements WorkflowStreamEvent {
        @Override
        public String eventType() { return "iteration_started"; }
    }

    /** 每完成一轮迭代后推送，携带当前轮次索引（0-based） */
    record IterationNextEvent(String runId, String nodeId, int iterationIndex)
            implements WorkflowStreamEvent {
        @Override
        public String eventType() { return "iteration_next"; }
    }

    /** 迭代节点全部轮次执行完成，携带聚合结果 */
    record IterationFinishedEvent(String runId, String nodeId, String status,
                                  StandardResultDTO result, NodeRunMetaDTO meta,
                                  ErrorInfoDTO error)
            implements WorkflowStreamEvent {
        @Override
        public String eventType() { return "iteration_finished"; }
    }
}
