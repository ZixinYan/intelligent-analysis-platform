package com.kuaishou.intelligentanalysisplatform.application.node;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecutor;
import org.springframework.stereotype.Component;

@Component
public class NodeExecutorRegistry {
    private final Map<String, NodeExecutor<?>> executorMap;

    public NodeExecutorRegistry(List<NodeExecutor<?>> executors) {
        this.executorMap = executors.stream()
                .collect(Collectors.toMap(NodeExecutor::supportType, Function.identity(), (left, right) -> left));
    }

    public NodeExecutor<?> get(String nodeType) {
        NodeExecutor<?> executor = executorMap.get(nodeType);
        if (executor == null) {
            throw new BaseBusinessException(ErrorCode.UNSUPPORTED_NODE_TYPE, "node executor not found");
        }
        return executor;
    }
}
