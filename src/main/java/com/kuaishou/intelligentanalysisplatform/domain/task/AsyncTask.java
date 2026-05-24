package com.kuaishou.intelligentanalysisplatform.domain.task;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsyncTask {
    private String taskId;
    private TaskType taskType;
    private String refId;
    private String tenantId;
    private String operatorId;
    private ExecutionStatus status;
    private String errorCode;
    private String errorMessage;
    private Long createdAt;
    private Long updatedAt;

    /** Returns true if the task can still be cancelled (QUEUED or RUNNING). */
    public boolean isCancellable() {
        return status == ExecutionStatus.QUEUED || status == ExecutionStatus.RUNNING;
    }

    /** Returns true if the task has reached a final, non-actionable state. */
    public boolean isTerminal() {
        return status == ExecutionStatus.SUCCEEDED
                || status == ExecutionStatus.FAILED
                || status == ExecutionStatus.CANCELLED
                || status == ExecutionStatus.SKIPPED;
    }

    /** Transition the in-memory state to CANCELLED. Caller must persist via repository. */
    public void cancel(String errorCode, String errorMessage) {
        this.status = ExecutionStatus.CANCELLED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.updatedAt = System.currentTimeMillis();
    }
}
