package com.kuaishou.intelligentanalysisplatform.application;

import com.kuaishou.intelligentanalysisplatform.contract.schema.ExportFileDTO;
import com.kuaishou.intelligentanalysisplatform.domain.export.ExportFile;

public interface ExportApplicationService {
    ExportFileDTO getFile(String fileId, String tenantId);
    ExportFile getFileEntity(String fileId, String tenantId);
}
