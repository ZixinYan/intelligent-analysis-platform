package com.kuaishou.intelligentanalysisplatform.contract.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportFileDTO {
    private String fileId;
    private String fileName;
    private String format;
    private Long fileSizeBytes;
    private Integer rowCount;
    private String downloadUrl;   // /api/v1/exports/{fileId}/download
    private Long createdAt;
    private Long expiresAt;       // 文件保留时间（默认 24h）
}
