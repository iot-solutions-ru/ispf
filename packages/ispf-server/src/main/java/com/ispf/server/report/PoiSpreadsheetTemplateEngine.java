package com.ispf.server.report;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellReference;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Band1 spreadsheet filler using Apache POI (ADR-0053).
 * Named range {@code Band1} marks the template row(s); placeholders {@code ${Band1.FIELD}} / {@code ${FIELD}}.
 */
@Component
public class PoiSpreadsheetTemplateEngine implements ReportTemplateEngine {

    private static final Pattern PLACEHOLDER = Pattern.compile(
            "\\$\\{(?:Band1\\.)?([A-Za-z0-9_]+)\\}",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SAME_ROW_RANGE = Pattern.compile(
            "([A-Z]+)\\$?(\\d+):([A-Z]+)\\$?(\\d+)",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public boolean supportsTemplateFormat(String templateFormat) {
        if (templateFormat == null) {
            return false;
        }
        String f = templateFormat.trim().toLowerCase();
        return "xlsx".equals(f) || "xls".equals(f);
    }

    @Override
    public void validate(byte[] content, String format) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Template file is empty");
        }
        ReportTemplateStore.validateFormat(format);
        if (!supportsTemplateFormat(format)) {
            throw new IllegalArgumentException("POI engine supports only xls/xlsx templates");
        }
        try (Workbook workbook = openWorkbook(content)) {
            BandRange band = requireBand1(workbook);
            boolean hasPlaceholder = false;
            Sheet sheet = workbook.getSheet(band.sheetName());
            for (int r = band.firstRow(); r <= band.lastRow(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                for (Cell cell : row) {
                    if (cell != null && cell.getCellType() == CellType.STRING) {
                        String v = cell.getStringCellValue();
                        if (v != null && PLACEHOLDER.matcher(v).find()) {
                            hasPlaceholder = true;
                            break;
                        }
                    }
                }
                if (hasPlaceholder) {
                    break;
                }
            }
            if (!hasPlaceholder) {
                throw new IllegalArgumentException(
                        "Band1 range has no ${Band1.FIELD} / ${FIELD} placeholders"
                );
            }
            // Smoke-fill one sample row to ensure workbook stays writable
            fillWorkbook(workbook, band, List.of(Map.of("SAMPLE", "ok")));
            writeWorkbook(workbook, format);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Invalid spreadsheet template: " + ex.getMessage()
                            + ". Named Excel range must be Band1 (Formulas → Name Manager) "
                            + "with ${Band1.FIELD} placeholders (UPPERCASE column names).",
                    ex
            );
        }
    }

    @Override
    public TemplateExportResult fill(
            ReportService.ReportView report,
            ReportTemplateStore.StoredTemplate template,
            List<Map<String, Object>> rows,
            ReportExportFormat ignoredOutputFormat
    ) {
        String templateFormat = template.format().trim().toLowerCase();
        if (!supportsTemplateFormat(templateFormat)) {
            throw new IllegalArgumentException("POI engine supports only xls/xlsx templates");
        }
        // Emit same binary family as the template; ReportExportService converts via LibreOffice if needed.
        // ignoredOutputFormat kept for SPI symmetry (PDF callers request XLSX fill then convert).
        if (ignoredOutputFormat == null) {
            throw new IllegalArgumentException("outputFormat is required");
        }
        try (Workbook workbook = openWorkbook(template.content())) {
            BandRange band = requireBand1(workbook);
            List<Map<String, Object>> prepared = YargReportService.prepareRows(rows == null ? List.of() : rows);
            fillWorkbook(workbook, band, prepared);
            byte[] bytes = writeWorkbook(workbook, templateFormat);
            String filename = safeFileName(report.title()) + "." + templateFormat;
            return new TemplateExportResult(bytes, filename, contentTypeFor(templateFormat));
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Report template fill failed: " + ex.getMessage(), ex);
        }
    }

    private static void fillWorkbook(Workbook workbook, BandRange band, List<Map<String, Object>> rows) {
        Sheet sheet = workbook.getSheet(band.sheetName());
        if (sheet == null) {
            throw new IllegalArgumentException("Band1 sheet not found: " + band.sheetName());
        }
        int bandHeight = band.lastRow() - band.firstRow() + 1;
        int dataCount = Math.max(rows.size(), 1);

        if (dataCount > 1) {
            int shiftStart = band.lastRow() + 1;
            int lastRow = sheet.getLastRowNum();
            if (shiftStart <= lastRow) {
                sheet.shiftRows(shiftStart, lastRow, (dataCount - 1) * bandHeight, true, false);
            }
            for (int i = 1; i < dataCount; i++) {
                int destFirst = band.firstRow() + i * bandHeight;
                copyBandRows(sheet, band.firstRow(), bandHeight, destFirst);
            }
        }

        for (int i = 0; i < dataCount; i++) {
            Map<String, Object> rowData = i < rows.size() ? rows.get(i) : Map.of();
            int rowStart = band.firstRow() + i * bandHeight;
            for (int r = 0; r < bandHeight; r++) {
                Row row = sheet.getRow(rowStart + r);
                if (row == null) {
                    continue;
                }
                for (Cell cell : row) {
                    if (cell == null || cell.getCellType() != CellType.STRING) {
                        continue;
                    }
                    String text = cell.getStringCellValue();
                    if (text == null || !text.contains("${")) {
                        continue;
                    }
                    applyPlaceholders(cell, text, rowData);
                }
            }
        }

        expandCollapsedRanges(sheet, band.firstRow() + 1, band.firstRow() + dataCount);
        updateBand1Name(workbook, band, dataCount, bandHeight);
    }

    private static void applyPlaceholders(Cell cell, String text, Map<String, Object> rowData) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuffer buffer = new StringBuffer();
        boolean any = false;
        Object soleValue = null;
        int matches = 0;
        while (matcher.find()) {
            any = true;
            matches++;
            String key = matcher.group(1).toUpperCase();
            Object value = resolve(rowData, key);
            soleValue = value;
            String replacement = value == null ? "" : String.valueOf(value);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        if (!any) {
            return;
        }
        // Whole-cell placeholder → keep numeric/boolean when possible
        if (matches == 1 && text.trim().matches("(?i)\\$\\{(?:Band1\\.)?[A-Za-z0-9_]+\\}")) {
            setCellValue(cell, soleValue);
        } else {
            cell.setCellValue(buffer.toString());
        }
    }

    private static Object resolve(Map<String, Object> rowData, String upperKey) {
        if (rowData.containsKey(upperKey)) {
            return rowData.get(upperKey);
        }
        String lower = upperKey.toLowerCase();
        if (rowData.containsKey(lower)) {
            return rowData.get(lower);
        }
        return null;
    }

    private static void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }
        if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
            return;
        }
        cell.setCellValue(String.valueOf(value));
    }

    private static void copyBandRows(Sheet sheet, int srcFirst, int bandHeight, int destFirst) {
        for (int r = 0; r < bandHeight; r++) {
            Row src = sheet.getRow(srcFirst + r);
            if (src == null) {
                continue;
            }
            Row dest = sheet.getRow(destFirst + r);
            if (dest == null) {
                dest = sheet.createRow(destFirst + r);
            }
            dest.setHeight(src.getHeight());
            for (Cell srcCell : src) {
                if (srcCell == null) {
                    continue;
                }
                Cell destCell = dest.getCell(srcCell.getColumnIndex());
                if (destCell == null) {
                    destCell = dest.createCell(srcCell.getColumnIndex());
                }
                cloneCell(srcCell, destCell);
            }
        }
    }

    private static void cloneCell(Cell src, Cell dest) {
        CellStyle style = src.getCellStyle();
        if (style != null) {
            dest.setCellStyle(style);
        }
        switch (src.getCellType()) {
            case STRING -> dest.setCellValue(src.getStringCellValue());
            case NUMERIC -> dest.setCellValue(src.getNumericCellValue());
            case BOOLEAN -> dest.setCellValue(src.getBooleanCellValue());
            case FORMULA -> dest.setCellFormula(src.getCellFormula());
            case BLANK -> dest.setBlank();
            default -> dest.setCellValue(src.toString());
        }
    }

    /**
     * Expand Excel ranges that collapsed to a single band row, e.g. {@code SUM(B2:B2)} → {@code SUM(B2:B4)}.
     */
    private static void expandCollapsedRanges(Sheet sheet, int excelBandFirstRow, int excelBandLastRow) {
        if (excelBandLastRow <= excelBandFirstRow) {
            return;
        }
        for (Row row : sheet) {
            if (row == null) {
                continue;
            }
            for (Cell cell : row) {
                if (cell == null || cell.getCellType() != CellType.FORMULA) {
                    continue;
                }
                String formula = cell.getCellFormula();
                Matcher m = SAME_ROW_RANGE.matcher(formula);
                StringBuffer buffer = new StringBuffer();
                boolean changed = false;
                while (m.find()) {
                    int r1 = Integer.parseInt(m.group(2));
                    int r2 = Integer.parseInt(m.group(4));
                    if (r1 == r2 && r1 == excelBandFirstRow) {
                        changed = true;
                        m.appendReplacement(
                                buffer,
                                Matcher.quoteReplacement(
                                        m.group(1) + r1 + ":" + m.group(3) + excelBandLastRow
                                )
                        );
                    } else {
                        m.appendReplacement(buffer, Matcher.quoteReplacement(m.group(0)));
                    }
                }
                m.appendTail(buffer);
                if (changed) {
                    cell.setCellFormula(buffer.toString());
                }
            }
        }
    }

    private static void updateBand1Name(Workbook workbook, BandRange band, int dataCount, int bandHeight) {
        Name name = findBand1Name(workbook);
        if (name == null) {
            return;
        }
        int last = band.firstRow() + dataCount * bandHeight - 1;
        String ref = new AreaReference(
                new CellReference(band.sheetName(), band.firstRow(), band.firstCol(), true, true),
                new CellReference(band.sheetName(), last, band.lastCol(), true, true),
                workbook.getSpreadsheetVersion()
        ).formatAsString();
        name.setRefersToFormula(ref);
    }

    private static BandRange requireBand1(Workbook workbook) {
        Name name = findBand1Name(workbook);
        if (name == null || name.getRefersToFormula() == null || name.getRefersToFormula().isBlank()) {
            throw new IllegalArgumentException(
                    "Named Excel range Band1 is required (Formulas → Name Manager)"
            );
        }
        AreaReference area = new AreaReference(name.getRefersToFormula(), workbook.getSpreadsheetVersion());
        CellReference first = area.getFirstCell();
        CellReference last = area.getLastCell();
        String sheetName = first.getSheetName();
        if (sheetName == null || sheetName.isBlank()) {
            // Area without sheet — use first sheet
            sheetName = workbook.getSheetAt(0).getSheetName();
        }
        return new BandRange(
                sheetName,
                Math.min(first.getRow(), last.getRow()),
                Math.max(first.getRow(), last.getRow()),
                Math.min(first.getCol(), last.getCol()),
                Math.max(first.getCol(), last.getCol())
        );
    }

    private static Name findBand1Name(Workbook workbook) {
        for (Name name : workbook.getAllNames()) {
            if (name != null && name.getNameName() != null && "Band1".equalsIgnoreCase(name.getNameName())) {
                return name;
            }
        }
        return null;
    }

    private static Workbook openWorkbook(byte[] content) throws Exception {
        return WorkbookFactory.create(new ByteArrayInputStream(content));
    }

    private static byte[] writeWorkbook(Workbook workbook, String format) throws Exception {
        // Keep original workbook type when possible
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        return out.toByteArray();
    }

    private static String contentTypeFor(String format) {
        return "xls".equalsIgnoreCase(format)
                ? ReportExportFormat.XLS.contentType()
                : ReportExportFormat.XLSX.contentType();
    }

    private static String safeFileName(String title) {
        if (title == null || title.isBlank()) {
            return "report";
        }
        String sanitized = title.replaceAll("[^a-zA-Z0-9._\\- ]", "_").trim();
        return sanitized.isEmpty() ? "report" : sanitized;
    }

    private record BandRange(String sheetName, int firstRow, int lastRow, int firstCol, int lastCol) {
    }
}
