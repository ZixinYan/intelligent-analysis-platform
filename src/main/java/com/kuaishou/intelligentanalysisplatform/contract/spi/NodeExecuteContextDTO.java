package com.kuaishou.intelligentanalysisplatform.contract.spi;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowNodeDTO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NodeExecuteContextDTO {
    private String workflowId;
    private String runId;
    private String nodeId;
    private Map<String, StandardResultDTO> upstreamResults;
    private RequestContextDTO requestContext;
    /** 当前工作流所有节点定义（ErrorHandlerNode retry 时用于找到被保护节点） */
    private List<WorkflowNodeDTO> allNodes;
}
