package com.kuaishou.intelligentanalysisplatform.interfaces.rest.v1;

import com.kuaishou.intelligentanalysisplatform.application.WorkflowApplicationService;
import com.kuaishou.intelligentanalysisplatform.common.response.ApiResponse;
import com.kuaishou.intelligentanalysisplatform.common.response.PageResult;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowDefinitionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowQueryRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.WorkflowSaveRequestDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowController {
    private final WorkflowApplicationService workflowApplicationService;

    @PostMapping
    public ApiResponse<WorkflowDefinitionDTO> create(@Valid @RequestBody WorkflowSaveRequestDTO request,
                                                     @RequestHeader("X-Tenant-Id") String tenantId,
                                                     @RequestHeader("X-User-Id") String userId) {
        request.setContext(contextOf(tenantId, userId));
        return ApiResponse.success(workflowApplicationService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<WorkflowDefinitionDTO> update(@PathVariable String id,
                                                     @Valid @RequestBody WorkflowSaveRequestDTO request,
                                                     @RequestHeader("X-Tenant-Id") String tenantId,
                                                     @RequestHeader("X-User-Id") String userId) {
        request.setContext(contextOf(tenantId, userId));
        return ApiResponse.success(workflowApplicationService.update(id, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<WorkflowDefinitionDTO> getById(@PathVariable String id,
                                                      @RequestHeader("X-Tenant-Id") String tenantId,
                                                      @RequestHeader("X-User-Id") String userId) {
        return ApiResponse.success(workflowApplicationService.getById(id, contextOf(tenantId, userId)));
    }

    @GetMapping
    public ApiResponse<PageResult<WorkflowDefinitionDTO>> list(@ModelAttribute WorkflowQueryRequestDTO request,
                                                               @RequestHeader("X-Tenant-Id") String tenantId,
                                                               @RequestHeader("X-User-Id") String userId) {
        request.setContext(contextOf(tenantId, userId));
        return ApiResponse.success(workflowApplicationService.list(request));
    }

    private RequestContextDTO contextOf(String tenantId, String userId) {
        return RequestContextDTO.builder()
                .tenantId(tenantId)
                .userId(userId)
                .requestId(null)
                .build();
    }
}
