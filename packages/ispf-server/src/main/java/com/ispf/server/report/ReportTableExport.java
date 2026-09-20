package com.ispf.server.report;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Serialises a report run result ({@code columns} / {@code rows} / {@code title} / {@code truncated}
 * map as produced by {@link ReportService#run}) into tabular download formats.
 */
final class ReportTableExport {

    private ReportTableExport() {
    }

    static byte[] toCsv(Map<String, Object> result) {
        List<Map<String, String>> columns = columns(result);
        List<Map<String, Object>> rows = rows(result);

        StringBuilder csv = new StringBuilder();
        List<String> fields = columns.stream().map(col -> col.get("field")).toList();
        csv.append(columns.stream().map(col -> escapeCsv(col.get("label"))).reduce((a, b) -> a + "," + b).orElse(""));
        csv.append('\n');
        for (Map<String, Object> row : rows) {
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) {
                    csv.append(',');
                }
                Object value = row.get(fields.get(i));
                csv.append(escapeCsv(value == null ? "" : value.toString()));
            }
            csv.append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    static byte[] toHtmlTable(Map<String, Object> result) {
        List<Map<String, String>> columns = columns(result);
        List<Map<String, Object>> rows = rows(result);
        String title = String.valueOf(result.getOrDefault("title", "Report"));
        boolean truncated = Boolean.TRUE.equals(result.get("truncated"));
        int rowCount = result.get("rowCount") instanceof Number number ? number.intValue() : rows.size();

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>");
        html.append(escapeHtml(title));
        html.append("</title><style>");
        html.append("body{font-family:system-ui,sans-serif;margin:1.5rem;color:#111}");
        html.append("table{border-collapse:collapse;width:100%;font-size:14px}");
        html.append("th,td{border:1px solid #ccc;padding:0.45rem 0.65rem;text-align:left}");
        html.append("th{background:#f3f4f6}.note{color:#666;font-size:13px;margin:0 0 1rem}");
        html.append("</style></head><body><h1>");
        html.append(escapeHtml(title));
        html.append("</h1>");
        if (truncated) {
            html.append("<p class=\"note\">Показаны первые ");
            html.append(rowCount);
            html.append(" строк (truncated).</p>");
        }
        html.append("<table><thead><tr>");
        List<String> fields = columns.stream().map(col -> col.get("field")).toList();
        for (Map<String, String> column : columns) {
            html.append("<th>").append(escapeHtml(column.get("label"))).append("</th>");
        }
        html.append("</tr></thead><tbody>");
        for (Map<String, Object> row : rows) {
            html.append("<tr>");
            for (String field : fields) {
                Object value = row.get(field);
                html.append("<td>").append(escapeHtml(value == null ? "" : value.toString())).append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</tbody></table></body></html>");
        return html.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Office Open XML (.xlsx). */
    static byte[] toXlsxTable(Map<String, Object> result) {
        return toWorkbookTable(result, XSSFWorkbook::new, "XLSX");
    }

    /** Legacy BIFF (.xls). */
    static byte[] toXlsTable(Map<String, Object> result) {
        return toWorkbookTable(result, HSSFWorkbook::new, "XLS");
    }

    private static byte[] toWorkbookTable(Map<String, Object> result, Supplier<Workbook> workbookFactory, String label) {
        List<Map<String, String>> columns = columns(result);
        List<Map<String, Object>> rows = rows(result);
        List<String> fields = columns.stream().map(col -> col.get("field")).toList();

        try (Workbook workbook = workbookFactory.get(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Report");
            Row header = sheet.createRow(0);
            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                header.createCell(columnIndex).setCellValue(columns.get(columnIndex).get("label"));
            }
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                Map<String, Object> values = rows.get(rowIndex);
                for (int columnIndex = 0; columnIndex < fields.size(); columnIndex++) {
                    Object value = values.get(fields.get(columnIndex));
                    row.createCell(columnIndex).setCellValue(value == null ? "" : String.valueOf(value));
                }
            }
            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                sheet.autoSizeColumn(columnIndex);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException(label + " table export failed: " + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> columns(Map<String, Object> result) {
        return (List<Map<String, String>>) result.get("columns");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("rows");
    }

    static String escapeHtml(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    static String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
