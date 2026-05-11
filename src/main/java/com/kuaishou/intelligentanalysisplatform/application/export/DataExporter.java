package com.kuaishou.intelligentanalysisplatform.application.export;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ExportFormat;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ExportOutputNodeConfigDTO;

public interface DataExporter {
    ExportFormat supportFormat();

    /** 将 rows 写入 outputStream */
    void export(List<Map<String, Object>> rows,
                List<String> columns,
                ExportOutputNodeConfigDTO config,
                OutputStream outputStream) throws IOException;
}
