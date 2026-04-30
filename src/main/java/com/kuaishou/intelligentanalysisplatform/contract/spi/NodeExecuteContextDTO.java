package com.kuaishou.intelligentanalysisplatform.contract.spi;

import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
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
}
