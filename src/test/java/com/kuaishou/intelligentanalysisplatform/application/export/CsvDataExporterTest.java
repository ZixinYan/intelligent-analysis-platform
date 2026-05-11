package com.kuaishou.intelligentanalysisplatform.application.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.schema.ExportOutputNodeConfigDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CsvDataExporterTest {

    private final CsvDataExporter exporter = new CsvDataExporter();

    private String export(List<Map<String, Object>> rows, List<String> columns,
                          ExportOutputNodeConfigDTO config) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.export(rows, columns, config, out);
        return out.toString("UTF-8");
    }

    @Test
    void normalExport_producesCorrectCsv() throws IOException {
        var rows = List.of(
                Map.<String, Object>of("name", "Alice", "age", "30"),
                Map.<String, Object>of("name", "Bob", "age", "25")
        );
        var columns = List.of("name", "age");
        var config = ExportOutputNodeConfigDTO.builder().build();

        String csv = export(rows, columns, config);

        String[] lines = csv.split("\n");
        assertEquals("name,age", lines[0]);
        assertEquals("Alice,30", lines[1]);
        assertEquals("Bob,25", lines[2]);
    }

    @Test
    void valueWithComma_isQuoted() throws IOException {
        var rows = List.of(Map.<String, Object>of("desc", "hello, world"));
        var columns = List.of("desc");
        var config = ExportOutputNodeConfigDTO.builder().build();

        String csv = export(rows, columns, config);

        assertTrue(csv.contains("\"hello, world\""));
    }

    @Test
    void valueWithNewline_isQuoted() throws IOException {
        var rows = List.of(Map.<String, Object>of("notes", "line1\nline2"));
        var columns = List.of("notes");
        var config = ExportOutputNodeConfigDTO.builder().build();

        String csv = export(rows, columns, config);

        assertTrue(csv.contains("\"line1\nline2\""));
    }

    @Test
    void valueWithDoubleQuote_isEscaped() throws IOException {
        var rows = List.of(Map.<String, Object>of("val", "say \"hi\""));
        var columns = List.of("val");
        var config = ExportOutputNodeConfigDTO.builder().build();

        String csv = export(rows, columns, config);

        assertTrue(csv.contains("\"say \"\"hi\"\"\""));
    }

    @Test
    void noHeader_omitsHeaderLine() throws IOException {
        var rows = List.of(Map.<String, Object>of("name", "Alice"));
        var columns = List.of("name");
        var config = ExportOutputNodeConfigDTO.builder().includeHeader(false).build();

        String csv = export(rows, columns, config);

        assertFalse(csv.startsWith("name"));
        assertTrue(csv.trim().equals("Alice"));
    }

    @Test
    void customDelimiter_usedInOutput() throws IOException {
        var rows = List.of(Map.<String, Object>of("a", "1", "b", "2"));
        var columns = List.of("a", "b");
        var config = ExportOutputNodeConfigDTO.builder().csvDelimiter(";").build();

        String csv = export(rows, columns, config);

        String[] lines = csv.split("\n");
        assertEquals("a;b", lines[0]);
        assertEquals("1;2", lines[1]);
    }

    @Test
    void missingColumn_usesEmptyString() throws IOException {
        var rows = List.of(Map.<String, Object>of("name", "Alice"));
        var columns = List.of("name", "missing");
        var config = ExportOutputNodeConfigDTO.builder().build();

        String csv = export(rows, columns, config);

        String[] lines = csv.split("\n");
        assertEquals("Alice,", lines[1]);
    }
}
