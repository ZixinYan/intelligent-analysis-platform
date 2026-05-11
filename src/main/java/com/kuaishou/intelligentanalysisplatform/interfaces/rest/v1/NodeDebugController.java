package com.kuaishou.intelligentanalysisplatform.interfaces.rest.v1;

import com.kuaishou.intelligentanalysisplatform.application.node.NodeExecuteDispatcher;
import com.kuaishou.intelligentanalysisplatform.common.response.ApiResponse;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeDebugRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/node-debug")
@RequiredArgsConstructor
public class NodeDebugController {

    private final NodeExecuteDispatcher nodeExecuteDispatcher;

    @PostMapping
    public ApiResponse<NodeResultDTO> debug(
            @RequestBody NodeDebugRequestDTO request,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        if (request.getContext() == null) {
            request.setContext(RequestContextDTO.builder()
                    .tenantId(tenantId)
                    .userId(userId)
                    .build());
        }
        NodeExecuteContextDTO context = nodeExecuteDispatcher.buildContext(request);
        NodeResultDTO result = nodeExecuteDispatcher.dispatch(request.getNode(), context);
        return ApiResponse.success(result);
    }
}
