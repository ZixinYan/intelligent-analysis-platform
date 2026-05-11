package com.kuaishou.intelligentanalysisplatform.application.export;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ExportFormat;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ExportOutputNodeConfigDTO;
import org.springframework.stereotype.Component;

@Component
public class CsvDataExporter implements DataExporter {

    @Override
    public ExportFormat supportFormat() {
        return ExportFormat.CSV;
    }

    @Override
    public void export(List<Map<String, Object>> rows, List<String> columns,
                       ExportOutputNodeConfigDTO config, OutputStream out) throws IOException {
        char delimiter = config.getCsvDelimiter() != null && !config.getCsvDelimiter().isEmpty()
                ? config.getCsvDelimiter().charAt(0) : ',';
        boolean includeHeader = !Boolean.FALSE.equals(config.getIncludeHeader());
        // OutputStreamWriter 不 close out（由调用方管理），使用不关闭外层 stream 的写法
        OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        if (includeHeader) {
            writer.write(joinCsvLine(columns, delimiter));
            writer.write("\n");
        }
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                List<String> values = columns.stream()
                        .map(col -> {
                            Object v = row.getOrDefault(col, "");
                            return escapeCsvValue(v == null ? "" : v.toString(), delimiter);
                        })
                        .toList();
                writer.write(joinCsvLine(values, delimiter));
                writer.write("\n");
            }
        }
        writer.flush();
    }

    private String joinCsvLine(List<String> values, char delimiter) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(delimiter);
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    private String escapeCsvValue(String value, char delimiter) {
        if (value.contains(String.valueOf(delimiter)) || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
