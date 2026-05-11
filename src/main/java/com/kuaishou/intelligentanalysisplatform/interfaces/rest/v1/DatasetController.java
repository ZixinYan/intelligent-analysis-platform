package com.kuaishou.intelligentanalysisplatform.interfaces.rest.v1;

import java.util.List;

import com.kuaishou.intelligentanalysisplatform.application.DatasetApplicationService;
import com.kuaishou.intelligentanalysisplatform.common.response.ApiResponse;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SaveDatasetRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.SavedDatasetSummaryDTO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/datasets")
@RequiredArgsConstructor
public class DatasetController {

    private final DatasetApplicationService datasetApplicationService;

    @PostMapping
    public ApiResponse<SavedDatasetSummaryDTO> save(
            @RequestBody SaveDatasetRequestDTO request,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        return ApiResponse.success(datasetApplicationService.save(request, tenantId, userId));
    }

    @GetMapping
    public ApiResponse<List<SavedDatasetSummaryDTO>> list(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Long beforeUpdatedAt) {
        return ApiResponse.success(datasetApplicationService.list(tenantId, limit, beforeUpdatedAt));
    }

    @GetMapping("/{datasetId}")
    public ApiResponse<SavedDatasetSummaryDTO> getSummary(
            @PathVariable String datasetId,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId) {
        return ApiResponse.success(datasetApplicationService.getSummary(datasetId, tenantId));
    }

    @GetMapping("/{datasetId}/rows")
    public ApiResponse<DatasetDTO> getRows(
            @PathVariable String datasetId,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int pageSize) {
        return ApiResponse.success(datasetApplicationService.getDatasetPage(datasetId, tenantId, page, pageSize));
    }

    @DeleteMapping("/{datasetId}")
    public ApiResponse<Void> delete(
            @PathVariable String datasetId,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId) {
        datasetApplicationService.delete(datasetId, tenantId);
        return ApiResponse.success();
    }

    @PatchMapping("/{datasetId}")
    public ApiResponse<Void> updateMeta(
            @PathVariable String datasetId,
            @RequestBody UpdateMetaRequestBody body,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId) {
        datasetApplicationService.updateMeta(datasetId, tenantId,
                body == null ? null : body.getName(),
                body == null ? null : body.getDescription());
        return ApiResponse.success();
    }

    @Data
    public static class UpdateMetaRequestBody {
        private String name;
        private String description;
    }
}
