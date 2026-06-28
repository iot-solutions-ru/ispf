import type { SheetValues } from "./sheetFormulaEngine";
import type { SheetRuntimeMeta, SheetTabMeta } from "./sheetRuntimeMeta";

export interface SheetTabData extends SheetTabMeta {
  contents: SheetValues;
}

export interface SheetWorkbook {
  activeSheetIndex: number;
  sheets: SheetTabData[];
}

const QUALIFIED_CELL_REF = /^([^!]+)!([A-Z]+\d+)$/i;

export function qualifyCellRef(sheetName: string, address: string): string {
  return `${sheetName}!${address.toUpperCase()}`;
}

export function parseQualifiedCellRef(
  ref: string
): { sheetName: string | null; address: string } {
  const match = QUALIFIED_CELL_REF.exec(ref.trim());
  if (!match) {
    return { sheetName: null, address: ref.trim().toUpperCase() };
  }
  return { sheetName: match[1], address: match[2].toUpperCase() };
}

export function sheetTabToRuntimeMeta(tab: SheetTabData): SheetRuntimeMeta {
  return {
    rows: tab.rows,
    cols: tab.cols,
    cellStyles: tab.cellStyles,
    mergedCells: tab.mergedCells,
    columnWidths: tab.columnWidths,
    rowHeights: tab.rowHeights,
  };
}

export function createDefaultWorkbook(rows: number, cols: number, name = "Sheet1"): SheetWorkbook {
  return {
    activeSheetIndex: 0,
    sheets: [{ name, rows, cols, contents: {} }],
  };
}

export function isMultiSheetWorkbook(meta: SheetRuntimeMeta | null | undefined): boolean {
  return Boolean(meta?.sheets && meta.sheets.length > 0);
}

export function extractContentsForSheet(allValues: SheetValues, sheetName: string): SheetValues {
  const result: SheetValues = {};
  const prefix = `${sheetName}!`;
  for (const [key, value] of Object.entries(allValues)) {
    const upper = key.toUpperCase();
    if (upper.startsWith(prefix.toUpperCase())) {
      result[upper.slice(prefix.length)] = value;
      continue;
    }
    const parsed = parseQualifiedCellRef(key);
    if (parsed.sheetName?.toLowerCase() === sheetName.toLowerCase()) {
      result[parsed.address] = value;
    }
  }
  return result;
}

export function unqualifiedValues(allValues: SheetValues): SheetValues {
  const result: SheetValues = {};
  for (const [key, value] of Object.entries(allValues)) {
    const parsed = parseQualifiedCellRef(key);
    if (parsed.sheetName) {
      continue;
    }
    result[parsed.address] = value;
  }
  return result;
}

export function workbookFromPersist(
  allValues: SheetValues,
  meta: SheetRuntimeMeta | null,
  defaultRows: number,
  defaultCols: number
): SheetWorkbook {
  if (meta?.sheets?.length) {
    const activeSheetIndex = Math.min(
      Math.max(meta.activeSheetIndex ?? 0, 0),
      meta.sheets.length - 1
    );
    const sheets: SheetTabData[] = meta.sheets.map((tab) => ({
      name: tab.name,
      rows: tab.rows,
      cols: tab.cols,
      cellStyles: tab.cellStyles,
      mergedCells: tab.mergedCells,
      columnWidths: tab.columnWidths,
      rowHeights: tab.rowHeights,
      contents: extractContentsForSheet(allValues, tab.name),
    }));
    return { activeSheetIndex, sheets };
  }

  const legacyName = "Sheet1";
  const unqualified = unqualifiedValues(allValues);
  const hasQualified = Object.keys(allValues).some((key) => parseQualifiedCellRef(key).sheetName);
  if (hasQualified) {
    const names = new Set<string>();
    for (const key of Object.keys(allValues)) {
      const parsed = parseQualifiedCellRef(key);
      if (parsed.sheetName) {
        names.add(parsed.sheetName);
      }
    }
    const sheets: SheetTabData[] = [...names].map((name) => ({
      name,
      rows: meta?.rows ?? defaultRows,
      cols: meta?.cols ?? defaultCols,
      contents: extractContentsForSheet(allValues, name),
      cellStyles: meta?.cellStyles,
      mergedCells: meta?.mergedCells,
      columnWidths: meta?.columnWidths,
      rowHeights: meta?.rowHeights,
    }));
    return { activeSheetIndex: 0, sheets: sheets.length > 0 ? sheets : createDefaultWorkbook(defaultRows, defaultCols).sheets };
  }

  return {
    activeSheetIndex: 0,
    sheets: [
      {
        name: legacyName,
        rows: meta?.rows ?? defaultRows,
        cols: meta?.cols ?? defaultCols,
        contents: unqualified,
        cellStyles: meta?.cellStyles,
        mergedCells: meta?.mergedCells,
        columnWidths: meta?.columnWidths,
        rowHeights: meta?.rowHeights,
      },
    ],
  };
}

export function workbookToPersist(workbook: SheetWorkbook): {
  values: SheetValues;
  meta: SheetRuntimeMeta;
} {
  const values: SheetValues = {};
  for (const sheet of workbook.sheets) {
    for (const [address, value] of Object.entries(sheet.contents)) {
      if (value.trim() === "") {
        continue;
      }
      values[qualifyCellRef(sheet.name, address)] = value;
    }
  }

  const active = workbook.sheets[workbook.activeSheetIndex] ?? workbook.sheets[0];
  const sheets: SheetTabMeta[] = workbook.sheets.map(
    ({ name, rows, cols, cellStyles, mergedCells, columnWidths, rowHeights }) => ({
      name,
      rows,
      cols,
      cellStyles,
      mergedCells,
      columnWidths,
      rowHeights,
    })
  );

  const meta: SheetRuntimeMeta = {
    activeSheetIndex: workbook.activeSheetIndex,
    sheets,
    rows: active?.rows,
    cols: active?.cols,
    cellStyles: active?.cellStyles,
    mergedCells: active?.mergedCells,
    columnWidths: active?.columnWidths,
    rowHeights: active?.rowHeights,
  };

  return { values, meta };
}

export function syncActiveSheetInWorkbook(
  workbook: SheetWorkbook,
  contents: SheetValues,
  meta: SheetRuntimeMeta | null
): SheetWorkbook {
  const index = Math.min(Math.max(workbook.activeSheetIndex, 0), workbook.sheets.length - 1);
  const current = workbook.sheets[index];
  if (!current) {
    return workbook;
  }
  const sheets = [...workbook.sheets];
  sheets[index] = {
    ...current,
    contents: { ...contents },
    rows: meta?.rows ?? current.rows,
    cols: meta?.cols ?? current.cols,
    cellStyles: meta?.cellStyles ?? current.cellStyles,
    mergedCells: meta?.mergedCells ?? current.mergedCells,
    columnWidths: meta?.columnWidths ?? current.columnWidths,
    rowHeights: meta?.rowHeights ?? current.rowHeights,
  };
  return { ...workbook, activeSheetIndex: index, sheets };
}

export function activeSheetData(workbook: SheetWorkbook): SheetTabData {
  const index = Math.min(Math.max(workbook.activeSheetIndex, 0), workbook.sheets.length - 1);
  return workbook.sheets[index] ?? workbook.sheets[0];
}
