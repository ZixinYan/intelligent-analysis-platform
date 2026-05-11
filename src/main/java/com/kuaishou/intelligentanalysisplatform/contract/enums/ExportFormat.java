package com.kuaishou.intelligentanalysisplatform.contract.enums;

public enum ExportFormat {
    CSV("csv"),
    EXCEL("xlsx"),
    PARQUET("parquet");  // 首期不实现，预留枚举

    private final String extension;

    ExportFormat(String ext) {
        this.extension = ext;
    }

    public String getExtension() {
        return extension;
    }
}
