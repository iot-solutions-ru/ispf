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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class IncomesBand1XlsTemplateTest {

    @Test
    void fillsDevicePathForLabVirtualStatusColumns() throws Exception {
        byte[] template;
        try (InputStream input = getClass().getResourceAsStream("/yarg/incomes-band1.xls")) {
            assumeTrue(input != null, "missing incomes-band1.xls");
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
                "xls",
                "",
                true
        );
        ReportTemplateStore.StoredTemplate stored = new ReportTemplateStore.StoredTemplate(
                report.path(), "xls", template, Instant.now()
        );

        TemplateExportResult result = new PoiSpreadsheetTemplateEngine().fill(
                report,
                stored,
                List.of(
                        Map.of(
                                "DEVICEPATH", "root.platform.devices.lab-userA-01",
                                "ONLINE", true,
                                "LASTSEEN", "init",
                                "VALUE", "42"
                        ),
                        Map.of(
                                "DEVICEPATH", "root.platform.devices.lab-userB-01",
                                "ONLINE", true,
                                "LASTSEEN", "init",
                                "VALUE", "43"
                        )
                ),
                ReportExportFormat.XLS
        );

        Map<String, Object> runResult = Map.of(
                "rows",
                List.of(Map.of("devicepath", "root.platform.devices.lab-userA-01", "online", true))
        );

        byte[] content = result.content();
        assertTrue(content.length > 1000, "export size=" + content.length);
        assertFalse(
                ReportExportContentGuard.outputMissingReportData(content, runResult),
                "export should contain device path"
        );
        assertTrue(
                ReportExportContentGuard.binaryContainsText(content, "lab-userA-01")
                        || ReportExportContentGuard.binaryContainsText(content, "42"),
                "export should contain row values"
        );

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("root.platform.devices.lab-userA-01", stringValue(sheet.getRow(1).getCell(0)));
            assertEquals("root.platform.devices.lab-userB-01", stringValue(sheet.getRow(2).getCell(0)));
        }
    }

    private static String stringValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        return cell.getCellType() == CellType.STRING ? cell.getStringCellValue() : String.valueOf(cell);
    }
}
