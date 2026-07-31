package com.ispf.server.report;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PoiSpreadsheetTemplateEngineTest {

    private final PoiSpreadsheetTemplateEngine engine = new PoiSpreadsheetTemplateEngine();

    @Test
    void fillsBand1RowsFromIncomesBand1Xlsx() throws Exception {
        byte[] template;
        try (InputStream input = getClass().getResourceAsStream("/yarg/incomes-band1.xlsx")) {
            assumeTrue(input != null, "missing incomes-band1.xlsx");
            template = input.readAllBytes();
        }

        ReportService.ReportView report = new ReportService.ReportView(
                "root.platform.reports.lab",
                "Lab device status",
                "",
                "",
                "",
                "sql",
                "",
                "",
                List.of(),
                List.of(),
                Map.of(),
                100,
                0,
                "xlsx",
                "",
                true
        );
        ReportTemplateStore.StoredTemplate stored = new ReportTemplateStore.StoredTemplate(
                report.path(), "xlsx", template, Instant.now()
        );

        TemplateExportResult result = engine.fill(
                report,
                stored,
                List.of(
                        Map.of("DEVICEPATH", "root.platform.devices.lab-userA-01", "VALUE", 42),
                        Map.of("DEVICEPATH", "root.platform.devices.lab-userB-01", "VALUE", 43)
                ),
                ReportExportFormat.XLSX
        );

        assertTrue(result.content().length > 500);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(result.content()))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals(
                    "root.platform.devices.lab-userA-01",
                    stringValue(sheet.getRow(1).getCell(0))
            );
            assertEquals(42.0, sheet.getRow(1).getCell(1).getNumericCellValue(), 0.001);
            assertEquals(
                    "root.platform.devices.lab-userB-01",
                    stringValue(sheet.getRow(2).getCell(0))
            );
            assertEquals(43.0, sheet.getRow(2).getCell(1).getNumericCellValue(), 0.001);
        }
    }

    @Test
    void validatesMinimalBand1Xlsx() throws Exception {
        byte[] template;
        try (InputStream input = getClass().getResourceAsStream("/yarg/incomes-minimal.xlsx")) {
            assumeTrue(input != null);
            template = input.readAllBytes();
        }
        engine.validate(template, "xlsx");
    }

    private static String stringValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        return cell.getCellType() == CellType.STRING ? cell.getStringCellValue() : String.valueOf(cell);
    }
}
