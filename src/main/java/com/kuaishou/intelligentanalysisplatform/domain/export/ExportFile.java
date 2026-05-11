package com.kuaishou.intelligentanalysisplatform.domain.export;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ExportFormat;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExportFile {
    private String fileId;
    private String tenantId;
    private String fileName;
    private ExportFormat format;
    private String storagePath;      // 本地路径或 OSS key
    private Long fileSizeBytes;
    private Integer rowCount;
    private Long createdAt;
    private Long expiresAt;

    public boolean isExpired() {
        return expiresAt != null && System.currentTimeMillis() > expiresAt;
    }
}
