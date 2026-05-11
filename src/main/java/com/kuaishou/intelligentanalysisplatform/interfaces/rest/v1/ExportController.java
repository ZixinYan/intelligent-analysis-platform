package com.kuaishou.intelligentanalysisplatform.interfaces.rest.v1;

import com.kuaishou.intelligentanalysisplatform.application.ExportApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.export.ExportFileStore;
import com.kuaishou.intelligentanalysisplatform.common.response.ApiResponse;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExportFormat;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ExportFileDTO;
import com.kuaishou.intelligentanalysisplatform.domain.export.ExportFile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/exports")
public class ExportController {

    private final ExportApplicationService exportApplicationService;
    private final ExportFileStore exportFileStore;

    public ExportController(ExportApplicationService exportApplicationService,
                            ExportFileStore exportFileStore) {
        this.exportApplicationService = exportApplicationService;
        this.exportFileStore = exportFileStore;
    }

    @GetMapping("/{fileId}")
    public ApiResponse<ExportFileDTO> getFile(
            @PathVariable String fileId,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId) {
        return ApiResponse.success(exportApplicationService.getFile(fileId, tenantId));
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<StreamingResponseBody> download(
            @PathVariable String fileId,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId) {
        ExportFile file = exportApplicationService.getFileEntity(fileId, tenantId);
        StreamingResponseBody body = out -> {
            byte[] data = exportFileStore.load(file.getStoragePath());
            out.write(data);
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.getFileName() + "\"")
                .contentType(resolveMediaType(file.getFormat()))
                .body(body);
    }

    private MediaType resolveMediaType(ExportFormat format) {
        if (format == null) return MediaType.APPLICATION_OCTET_STREAM;
        return switch (format) {
            case CSV -> new MediaType("text", "csv");
            case EXCEL -> new MediaType("application",
                    "vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
