package com.kuaishou.intelligentanalysisplatform.application.impl;

import com.kuaishou.intelligentanalysisplatform.application.ExportApplicationService;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ExportFileDTO;
import com.kuaishou.intelligentanalysisplatform.domain.export.ExportFile;
import com.kuaishou.intelligentanalysisplatform.domain.export.ExportFileRepository;
import org.springframework.stereotype.Service;

@Service
public class DefaultExportApplicationService implements ExportApplicationService {

    private final ExportFileRepository exportFileRepository;

    public DefaultExportApplicationService(ExportFileRepository exportFileRepository) {
        this.exportFileRepository = exportFileRepository;
    }

    @Override
    public ExportFileDTO getFile(String fileId, String tenantId) {
        ExportFile file = getFileEntity(fileId, tenantId);
        return toDto(file);
    }

    @Override
    public ExportFile getFileEntity(String fileId, String tenantId) {
        ExportFile file = exportFileRepository.findByIdAndTenantId(fileId, tenantId)
                .orElseThrow(() -> new BaseBusinessException(ErrorCode.EXPORT_NOT_FOUND,
                        "export file not found: " + fileId));
        if (file.isExpired()) {
            throw new BaseBusinessException(ErrorCode.EXPORT_EXPIRED,
                    "export file has expired: " + fileId);
        }
        return file;
    }

    private ExportFileDTO toDto(ExportFile file) {
        return ExportFileDTO.builder()
                .fileId(file.getFileId())
                .fileName(file.getFileName())
                .format(file.getFormat() == null ? null : file.getFormat().name())
                .fileSizeBytes(file.getFileSizeBytes())
                .rowCount(file.getRowCount())
                .downloadUrl("/api/v1/exports/" + file.getFileId() + "/download")
                .createdAt(file.getCreatedAt())
                .expiresAt(file.getExpiresAt())
                .build();
    }
}
