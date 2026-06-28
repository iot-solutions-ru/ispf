import { rowColToA1 } from "./sheetAddress";
import {
  findUnsupportedFunctions,
  normalizeImportedFormula,
} from "./sheetFormulaNormalize";
import type { SheetRuntimeMeta } from "./sheetRuntimeMeta";
import { applyStylesAndMergesToWorksheet, importStylesAndMerges } from "./sheetXlsxStyles";
import type { SheetValues } from "./sheetFormulaEngine";
import type { SheetTabData, SheetWorkbook } from "./sheetWorkbook";
import { loadXlsxWorkbook } from "./sheetXlsxNormalize";

const MAX_IMPORT_ROWS = 500;
const MAX_IMPORT_COLS = 52;

export interface SheetXlsxSheetInfo {
  index: number;
  name: string;
}

export interface SheetXlsxImportResult {
  rows: number;
  cols: number;
  contents: SheetValues;
  sheetName: string;
  warnings: string[];
  meta: SheetRuntimeMeta;
}

export interface SheetXlsxWorkbookImportResult {
  workbook: SheetWorkbook;
  warnings: string[];
}

type ImportWorksheet = {
  name: string;
  rowCount: number;
  columnCount: number;
  dimensions?: { top?: number; left?: number; bottom?: number; right?: number };
  getCell: (row: number, col: number) => {
    formula?: string;
    value?: unknown;
    text?: string;
  };
  model?: { merges?: string[] };
  getColumn: (col: number) => { width?: number };
  getRow: (row: number) => { height?: number };
  mergeCells?: (range: string) => void;
};

function importWorksheet(worksheet: ImportWorksheet): SheetXlsxImportResult {
  const dims = worksheet.dimensions;
  const top = dims?.top ?? 1;
  const left = dims?.left ?? 1;
  const bottom = Math.min(dims?.bottom ?? worksheet.rowCount, top + MAX_IMPORT_ROWS - 1);
  const right = Math.min(dims?.right ?? worksheet.columnCount, left + MAX_IMPORT_COLS - 1);

  const rows = Math.max(1, bottom - top + 1);
  const cols = Math.max(1, right - left + 1);
  const contents: SheetValues = {};
  const unsupportedFns = new Set<string>();

  for (let r = top; r <= bottom; r++) {
    for (let c = left; c <= right; c++) {
      const cell = worksheet.getCell(r, c);
      const raw = cellContentFromExcelCell(cell);
      if (!raw) {
        continue;
      }
      const address = rowColToA1(r - top, c - left);
      contents[address] = raw;
      if (raw.startsWith("=")) {
        for (const fn of findUnsupportedFunctions(raw)) {
          unsupportedFns.add(fn);
        }
      }
    }
  }

  const { cellStyles, mergedCells, columnWidths, rowHeights } = importStylesAndMerges(
    worksheet as Parameters<typeof importStylesAndMerges>[0],
    top,
    left,
    bottom,
    right
  );

  const warningList = [...unsupportedFns].map((fn) => `#NAME? for unsupported function: ${fn}`);
  if ((dims?.bottom ?? 0) - top + 1 > MAX_IMPORT_ROWS) {
    warningList.push(`Grid truncated to ${MAX_IMPORT_ROWS} rows`);
  }
  if ((dims?.right ?? 0) - left + 1 > MAX_IMPORT_COLS) {
    warningList.push(`Grid truncated to ${MAX_IMPORT_COLS} columns`);
  }

  return {
    rows,
    cols,
    contents,
    sheetName: worksheet.name,
    warnings: warningList,
    meta: {
      rows,
      cols,
      cellStyles,
      mergedCells,
      columnWidths,
      rowHeights,
    },
  };
}

function formatExcelLiteral(value: unknown): string {
  if (value === null || value === undefined) {
    return "";
  }
  if (typeof value === "boolean") {
    return value ? "TRUE" : "FALSE";
  }
  if (typeof value === "number" && Number.isFinite(value)) {
    return String(value);
  }
  if (typeof value === "string") {
    return value;
  }
  if (value instanceof Date) {
    return value.toISOString();
  }
  if (typeof value === "object") {
    const obj = value as Record<string, unknown>;
    if (typeof obj.formula === "string") {
      return `=${normalizeImportedFormula(obj.formula)}`;
    }
    if (typeof obj.result !== "undefined") {
      return formatExcelLiteral(obj.result);
    }
    if (Array.isArray(obj.richText)) {
      return obj.richText.map((part) => String((part as { text?: string }).text ?? "")).join("");
    }
    if (typeof obj.text === "string") {
      return obj.text;
    }
    if (typeof obj.error === "string") {
      return obj.error;
    }
  }
  return String(value);
}

function cellContentFromExcelCell(cell: {
  formula?: string;
  value?: unknown;
  text?: string;
}): string {
  if (cell.formula) {
    return `=${normalizeImportedFormula(cell.formula)}`;
  }
  if (cell.value !== null && cell.value !== undefined) {
    return formatExcelLiteral(cell.value);
  }
  return cell.text ?? "";
}

export async function listXlsxSheets(file: File): Promise<SheetXlsxSheetInfo[]> {
  const ExcelJS = (await import("exceljs")).default;
  const workbook = await loadXlsxWorkbook(ExcelJS, await file.arrayBuffer());
  return workbook.worksheets.map((ws, index) => ({
    index,
    name: ws.name,
  }));
}

export async function importXlsxFile(
  file: File,
  sheetIndex = 0
): Promise<SheetXlsxImportResult> {
  const ExcelJS = (await import("exceljs")).default;
  const workbook = await loadXlsxWorkbook(ExcelJS, await file.arrayBuffer());
  const worksheet = workbook.worksheets[sheetIndex];
  if (!worksheet) {
    throw new Error("NO_SHEET");
  }
  return importWorksheet(worksheet);
}

export async function importXlsxWorkbook(file: File): Promise<SheetXlsxWorkbookImportResult> {
  const ExcelJS = (await import("exceljs")).default;
  const workbook = await loadXlsxWorkbook(ExcelJS, await file.arrayBuffer());
  if (workbook.worksheets.length === 0) {
    throw new Error("NO_SHEET");
  }

  const warnings = new Set<string>();
  const sheets: SheetTabData[] = workbook.worksheets.map((worksheet) => {
    const imported = importWorksheet(worksheet);
    for (const warning of imported.warnings) {
      warnings.add(`${imported.sheetName}: ${warning}`);
    }
    return {
      name: imported.sheetName,
      rows: imported.rows,
      cols: imported.cols,
      contents: imported.contents,
      cellStyles: imported.meta.cellStyles,
      mergedCells: imported.meta.mergedCells,
      columnWidths: imported.meta.columnWidths,
      rowHeights: imported.meta.rowHeights,
    };
  });

  return {
    workbook: { activeSheetIndex: 0, sheets },
    warnings: [...warnings],
  };
}

export interface SheetXlsxExportOptions {
  rows: number;
  cols: number;
  getCellEditContent: (address: string) => string;
  getCellValue: (address: string) => unknown;
  sheetName?: string;
  meta?: SheetRuntimeMeta;
}

export async function exportXlsxWorkbook(options: SheetXlsxExportOptions): Promise<Blob> {
  const ExcelJS = (await import("exceljs")).default;
  const workbook = new ExcelJS.Workbook();
  const worksheet = workbook.addWorksheet(options.sheetName?.slice(0, 31) || "Sheet1");

  writeWorksheetCells(
    worksheet,
    {
      name: options.sheetName ?? "Sheet1",
      rows: options.rows,
      cols: options.cols,
      contents: {},
      cellStyles: options.meta?.cellStyles,
      mergedCells: options.meta?.mergedCells,
      columnWidths: options.meta?.columnWidths,
      rowHeights: options.meta?.rowHeights,
    },
    options.getCellEditContent,
    options.getCellValue
  );

  const buffer = await workbook.xlsx.writeBuffer();
  return new Blob([buffer], {
    type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  });
}

function writeWorksheetCells(
  worksheet: ReturnType<import("exceljs").Workbook["addWorksheet"]>,
  sheet: SheetTabData,
  getCellEditContent: (address: string) => string,
  getCellValue: (address: string) => unknown
): void {
  applyStylesAndMergesToWorksheet(worksheet, {
    rows: sheet.rows,
    cols: sheet.cols,
    cellStyles: sheet.cellStyles,
    mergedCells: sheet.mergedCells,
    columnWidths: sheet.columnWidths,
    rowHeights: sheet.rowHeights,
  });

  for (let row = 0; row < sheet.rows; row++) {
    for (let col = 0; col < sheet.cols; col++) {
      const address = rowColToA1(row, col);
      const edit = getCellEditContent(address).trim();
      const cell = worksheet.getCell(row + 1, col + 1);
      if (edit.startsWith("=")) {
        cell.value = { formula: edit.slice(1) };
        continue;
      }
      if (edit !== "") {
        const num = Number.parseFloat(edit);
        if (Number.isFinite(num) && /^-?\d+(\.\d+)?$/.test(edit)) {
          cell.value = num;
        } else if (edit.toUpperCase() === "TRUE" || edit.toUpperCase() === "FALSE") {
          cell.value = edit.toUpperCase() === "TRUE";
        } else {
          cell.value = edit;
        }
        continue;
      }
      const computed = getCellValue(address);
      if (computed === null || computed === undefined || computed === "") {
        continue;
      }
      if (typeof computed === "number" && Number.isFinite(computed)) {
        cell.value = computed;
      } else if (typeof computed === "boolean") {
        cell.value = computed;
      } else {
        cell.value = String(computed);
      }
    }
  }
}

export interface SheetXlsxWorkbookExportOptions {
  workbook: SheetWorkbook;
  getSheetCellEditContent: (sheetIndex: number, address: string) => string;
  getSheetCellValue: (sheetIndex: number, address: string) => unknown;
}

export async function exportXlsxWorkbookFromTabs(
  options: SheetXlsxWorkbookExportOptions
): Promise<Blob> {
  const ExcelJS = (await import("exceljs")).default;
  const workbook = new ExcelJS.Workbook();

  for (let index = 0; index < options.workbook.sheets.length; index++) {
    const sheet = options.workbook.sheets[index];
    const worksheet = workbook.addWorksheet(sheet.name.slice(0, 31) || `Sheet${index + 1}`);
    writeWorksheetCells(
      worksheet,
      sheet,
      (address) => options.getSheetCellEditContent(index, address),
      (address) => options.getSheetCellValue(index, address)
    );
  }

  const buffer = await workbook.xlsx.writeBuffer();
  return new Blob([buffer], {
    type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  });
}
