package com.kuaishou.intelligentanalysisplatform.domain.analysis;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionMode;
import com.kuaishou.intelligentanalysisplatform.contract.schema.QueryOptionDTO;

public final class SyncAsyncBoundaryDecider {
    private static final int SYNC_LIMIT = 1000;
    private static final int SYNC_TIMEOUT_MS = 5000;

    private SyncAsyncBoundaryDecider() {
    }

    public static ExecutionMode decide(QueryOptionDTO option) {
        if (option == null) {
            return ExecutionMode.SYNC;
        }
        if (Boolean.TRUE.equals(option.getAsyncPreferred())) {
            return ExecutionMode.ASYNC;
        }
        Integer limit = option.getLimit();
        Integer timeoutMs = option.getTimeoutMs();
        if ((limit != null && limit > SYNC_LIMIT) || (timeoutMs != null && timeoutMs > SYNC_TIMEOUT_MS)) {
            return ExecutionMode.ASYNC;
        }
        return ExecutionMode.SYNC;
    }
}
