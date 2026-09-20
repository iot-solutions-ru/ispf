// Extracted from SpreadsheetImportNotice.tsx so that component modules only export components
// (react-refresh/only-export-components keeps Fast Refresh state-preserving).
import type { SheetImportReport } from "../sheet/sheetXlsx";

export function hasSpreadsheetImportIssues(report: SheetImportReport): boolean {
  return report.unsupportedFunctions.length > 0 || report.truncations.length > 0;
}
