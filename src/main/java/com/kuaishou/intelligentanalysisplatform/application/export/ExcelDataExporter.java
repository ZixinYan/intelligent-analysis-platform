package com.kuaishou.intelligentanalysisplatform.application.export;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.enums.ExportFormat;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ExportOutputNodeConfigDTO;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class ExcelDataExporter implements DataExporter {

    @Override
    public ExportFormat supportFormat() {
        return ExportFormat.EXCEL;
    }

    @Override
    public void export(List<Map<String, Object>> rows, List<String> columns,
                       ExportOutputNodeConfigDTO config, OutputStream out) throws IOException {
        boolean includeHeader = !Boolean.FALSE.equals(config.getIncludeHeader());
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("data");
            int rowIdx = 0;
            if (includeHeader) {
                Row headerRow = sheet.createRow(rowIdx++);
                for (int i = 0; i < columns.size(); i++) {
                    headerRow.createCell(i).setCellValue(columns.get(i));
                }
            }
            if (rows != null) {
                for (Map<String, Object> dataRow : rows) {
                    Row excelRow = sheet.createRow(rowIdx++);
                    for (int i = 0; i < columns.size(); i++) {
                        Object value = dataRow.getOrDefault(columns.get(i), "");
                        Cell cell = excelRow.createCell(i);
                        setCellValue(cell, value);
                    }
                }
            }
            workbook.write(out);
        }
    }

    private void setCellValue(Cell cell, Object value) {
        if (value instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else if (value instanceof Boolean b) {
            cell.setCellValue(b);
        } else {
            cell.setCellValue(value != null ? value.toString() : "");
        }
    }
}
