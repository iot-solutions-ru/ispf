package com.ispf.server.report;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportTableExportTest {

    private static Map<String, Object> result() {
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("device", "pump-1");
        row1.put("note", "a,b \"quoted\"");
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("device", "<tank>");
        row2.put("note", null);
        return Map.of(
                "title", "Shift & report",
                "columns", List.of(Map.of("field", "device", "label", "Device"), Map.of("field", "note", "label", "Note")),
                "rows", List.of(row1, row2),
                "rowCount", 2,
                "truncated", true
        );
    }

    @Test
    void csvEscapesDelimitersAndQuotesAndBlanksNulls() {
        String csv = new String(ReportTableExport.toCsv(result()), StandardCharsets.UTF_8);
        assertEquals("Device,Note\npump-1,\"a,b \"\"quoted\"\"\"\n<tank>,\n", csv);
    }

    @Test
    void htmlEscapesMarkupAndReportsTruncation() {
        String html = new String(ReportTableExport.toHtmlTable(result()), StandardCharsets.UTF_8);
        assertTrue(html.contains("<title>Shift &amp; report</title>"));
        assertTrue(html.contains("<td>&lt;tank&gt;</td>"));
        assertTrue(html.contains("truncated"));
        assertFalse(html.contains("<tank>"));
    }

    @Test
    void xlsxAndXlsProduceTheSameSheetThroughDifferentContainers() throws Exception {
        try (Workbook xlsx = new XSSFWorkbook(new ByteArrayInputStream(ReportTableExport.toXlsxTable(result())));
             Workbook xls = new HSSFWorkbook(new ByteArrayInputStream(ReportTableExport.toXlsTable(result())))) {
            for (Workbook workbook : List.of(xlsx, xls)) {
                Sheet sheet = workbook.getSheet("Report");
                assertEquals("Device", sheet.getRow(0).getCell(0).getStringCellValue());
                assertEquals("Note", sheet.getRow(0).getCell(1).getStringCellValue());
                assertEquals("pump-1", sheet.getRow(1).getCell(0).getStringCellValue());
                assertEquals("", sheet.getRow(2).getCell(1).getStringCellValue());
                assertEquals(2, sheet.getLastRowNum());
            }
        }
    }
}
