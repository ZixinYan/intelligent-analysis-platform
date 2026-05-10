package com.kuaishou.intelligentanalysisplatform.interfaces.rest.v1;

import com.kuaishou.intelligentanalysisplatform.application.DatasourceApplicationService;
import com.kuaishou.intelligentanalysisplatform.common.response.ApiResponse;
import com.kuaishou.intelligentanalysisplatform.common.response.PageResult;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceCreateRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceQueryRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceTestConnectionRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceTestConnectionResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceUpdateRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.OptionDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/datasources")
@RequiredArgsConstructor
public class DatasourceController {
    private final DatasourceApplicationService datasourceApplicationService;

    @PostMapping
    public ApiResponse<DatasourceDTO> create(@Valid @RequestBody DatasourceCreateRequestDTO request,
                                             @RequestHeader("X-Tenant-Id") String tenantId,
                                             @RequestHeader("X-User-Id") String userId) {
        request.setContext(contextOf(tenantId, userId));
        return ApiResponse.success(datasourceApplicationService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<DatasourceDTO> update(@PathVariable String id,
                                             @Valid @RequestBody DatasourceUpdateRequestDTO request,
                                             @RequestHeader("X-Tenant-Id") String tenantId,
                                             @RequestHeader("X-User-Id") String userId) {
        request.setContext(contextOf(tenantId, userId));
        return ApiResponse.success(datasourceApplicationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id,
                                    @RequestHeader("X-Tenant-Id") String tenantId,
                                    @RequestHeader("X-User-Id") String userId) {
        datasourceApplicationService.delete(id, contextOf(tenantId, userId));
        return ApiResponse.success();
    }

    @GetMapping("/{id}")
    public ApiResponse<DatasourceDTO> getById(@PathVariable String id,
                                              @RequestHeader("X-Tenant-Id") String tenantId,
                                              @RequestHeader("X-User-Id") String userId) {
        return ApiResponse.success(datasourceApplicationService.getById(id, contextOf(tenantId, userId)));
    }

    @GetMapping
    public ApiResponse<PageResult<DatasourceDTO>> list(@ModelAttribute DatasourceQueryRequestDTO request,
                                                       @RequestHeader("X-Tenant-Id") String tenantId,
                                                       @RequestHeader("X-User-Id") String userId) {
        request.setContext(contextOf(tenantId, userId));
        return ApiResponse.success(datasourceApplicationService.list(request));
    }

    @PostMapping("/{id}/test-connection")
    public ApiResponse<DatasourceTestConnectionResultDTO> testConnection(@PathVariable String id,
                                                                         @RequestHeader("X-Tenant-Id") String tenantId,
                                                                         @RequestHeader("X-User-Id") String userId) {
        return ApiResponse.success(datasourceApplicationService.testConnection(DatasourceTestConnectionRequestDTO.builder()
                .datasourceId(id)
                .context(contextOf(tenantId, userId))
                .build()));
    }

    @GetMapping("/{id}/tables")
    public ApiResponse<List<String>> listTables(@PathVariable String id,
                                                @RequestHeader("X-Tenant-Id") String tenantId,
                                                @RequestHeader("X-User-Id") String userId) {
        return ApiResponse.success(datasourceApplicationService.listTables(id, contextOf(tenantId, userId)));
    }

    @GetMapping("/options")
    public ApiResponse<List<OptionDTO>> listOptions(@ModelAttribute DatasourceQueryRequestDTO request,
                                                    @RequestHeader("X-Tenant-Id") String tenantId,
                                                    @RequestHeader("X-User-Id") String userId) {
        request.setContext(contextOf(tenantId, userId));
        PageResult<DatasourceDTO> page = datasourceApplicationService.list(request);
        List<OptionDTO> options = page.getItems().stream()
                .map(ds -> OptionDTO.builder().label(ds.getDatabase()).value(ds.getId()).build())
                .toList();
        return ApiResponse.success(options);
    }

    private RequestContextDTO contextOf(String tenantId, String userId) {
        return RequestContextDTO.builder()
                .tenantId(tenantId)
                .userId(userId)
                .requestId(null)
                .build();
    }
}
