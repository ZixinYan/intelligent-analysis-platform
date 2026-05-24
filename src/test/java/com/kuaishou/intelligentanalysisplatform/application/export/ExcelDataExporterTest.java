package com.kuaishou.intelligentanalysisplatform.application.export;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.schema.ExportOutputNodeConfigDTO;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExcelDataExporterTest {

    private final ExcelDataExporter exporter = new ExcelDataExporter();

    private Workbook exportAndGetWorkbook(List<Map<String, Object>> rows, List<String> columns,
                                          ExportOutputNodeConfigDTO config) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.export(rows, columns, config, out);
        return new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()));
    }

    @Test
    void normalExport_headerAndDataRowsPresent() throws IOException {
        var rows = List.of(
                Map.<String, Object>of("name", "Alice", "score", 95.5),
                Map.<String, Object>of("name", "Bob", "score", 80.0)
        );
        var columns = List.of("name", "score");
        var config = ExportOutputNodeConfigDTO.builder().build();

        try (Workbook wb = exportAndGetWorkbook(rows, columns, config)) {
            Sheet sheet = wb.getSheetAt(0);
            // Header row
            Row header = sheet.getRow(0);
            assertEquals("name", header.getCell(0).getStringCellValue());
            assertEquals("score", header.getCell(1).getStringCellValue());
            // Data rows
            Row row1 = sheet.getRow(1);
            assertEquals("Alice", row1.getCell(0).getStringCellValue());
            assertEquals(95.5, row1.getCell(1).getNumericCellValue(), 0.001);
            Row row2 = sheet.getRow(2);
            assertEquals("Bob", row2.getCell(0).getStringCellValue());
        }
    }

    @Test
    void numericValue_storedAsNumericCell() throws IOException {
        var rows = List.of(Map.<String, Object>of("count", 42));
        var columns = List.of("count");
        var config = ExportOutputNodeConfigDTO.builder().build();

        try (Workbook wb = exportAndGetWorkbook(rows, columns, config)) {
            Cell cell = wb.getSheetAt(0).getRow(1).getCell(0);
            assertEquals(42.0, cell.getNumericCellValue(), 0.001);
        }
    }

    @Test
    void booleanValue_storedAsBooleanCell() throws IOException {
        var rows = List.of(Map.<String, Object>of("active", true));
        var columns = List.of("active");
        var config = ExportOutputNodeConfigDTO.builder().build();

        try (Workbook wb = exportAndGetWorkbook(rows, columns, config)) {
            Cell cell = wb.getSheetAt(0).getRow(1).getCell(0);
            assertTrue(cell.getBooleanCellValue());
        }
    }

    @Test
    void nullValue_storedAsEmptyString() throws IOException {
        var rows = List.of(Map.<String, Object>of("name", "Alice"));
        var columns = List.of("name", "missing");
        var config = ExportOutputNodeConfigDTO.builder().build();

        try (Workbook wb = exportAndGetWorkbook(rows, columns, config)) {
            Cell cell = wb.getSheetAt(0).getRow(1).getCell(1);
            assertEquals("", cell.getStringCellValue());
        }
    }

    @Test
    void noHeader_firstRowIsData() throws IOException {
        var rows = List.of(Map.<String, Object>of("col", "value"));
        var columns = List.of("col");
        var config = ExportOutputNodeConfigDTO.builder().includeHeader(false).build();

        try (Workbook wb = exportAndGetWorkbook(rows, columns, config)) {
            Sheet sheet = wb.getSheetAt(0);
            // Row 0 should be data, not header
            Row first = sheet.getRow(0);
            assertEquals("value", first.getCell(0).getStringCellValue());
            // No row 1 (only 1 data row)
            assertNull(sheet.getRow(1));
        }
    }

    @Test
    void columnOrder_followsConfiguredColumns() throws IOException {
        var rows = List.of(Map.<String, Object>of("b", "B_val", "a", "A_val", "c", "C_val"));
        var columns = List.of("c", "a", "b");
        var config = ExportOutputNodeConfigDTO.builder().build();

        try (Workbook wb = exportAndGetWorkbook(rows, columns, config)) {
            Row header = wb.getSheetAt(0).getRow(0);
            assertEquals("c", header.getCell(0).getStringCellValue());
            assertEquals("a", header.getCell(1).getStringCellValue());
            assertEquals("b", header.getCell(2).getStringCellValue());

            Row data = wb.getSheetAt(0).getRow(1);
            assertEquals("C_val", data.getCell(0).getStringCellValue());
            assertEquals("A_val", data.getCell(1).getStringCellValue());
            assertEquals("B_val", data.getCell(2).getStringCellValue());
        }
    }
}
