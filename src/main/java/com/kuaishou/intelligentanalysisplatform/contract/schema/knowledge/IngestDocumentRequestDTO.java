package com.kuaishou.intelligentanalysisplatform.contract.schema.knowledge;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class IngestDocumentRequestDTO {
    @NotBlank
    private String docId;
    @NotBlank
    private String docTitle;
    @NotBlank
    private String content;
}
