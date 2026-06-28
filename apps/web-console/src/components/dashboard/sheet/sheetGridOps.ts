import type { SheetMergeRange } from "../../../types/dashboard";
import { a1ToRowCol, rowColToA1 } from "./sheetAddress";
import type { SheetValues } from "./sheetFormulaEngine";
import type { SheetRuntimeMeta } from "./sheetRuntimeMeta";
import { sheetTabToRuntimeMeta, type SheetTabData, type SheetWorkbook } from "./sheetWorkbook";

export type GridAxis = "row" | "col";
export type GridOpMode = "insert" | "delete";

const CROSS_RANGE_REF =
  /(?:'([^']+)'|([^'!\s(;,+*/&=<>]+))!(\$?[A-Z]+\$?\d+)(?::(\$?[A-Z]+\$?\d+))?/gi;
const LOCAL_RANGE_REF = /(?<![!:'\w])(\$?[A-Z]+\$?\d+)(?::(\$?[A-Z]+\$?\d+))?/gi;

function splitCellRef(ref: string): { colAbs: boolean; rowAbs: boolean; row: number; col: number } | null {
  const colAbs = ref.startsWith("$");
  const rowAbs = /\$\d+$/i.test(ref);
  const rc = a1ToRowCol(ref.replace(/\$/g, ""));
  if (!rc) {
    return null;
  }
  return { colAbs, rowAbs, row: rc.row, col: rc.col };
}

function joinCellRef(colAbs: boolean, rowAbs: boolean, row: number, col: number): string {
  const addr = rowColToA1(row, col);
  const colLetters = addr.replace(/\d+$/, "");
  const rowNum = addr.replace(/^[A-Z]+/i, "");
  if (colAbs && rowAbs) {
    return `$${colLetters}$${rowNum}`;
  }
  if (colAbs) {
    return `$${colLetters}${rowNum}`;
  }
  if (rowAbs) {
    return `${colLetters}$${rowNum}`;
  }
  return addr;
}

function shiftRefAddress(
  ref: string,
  axis: GridAxis,
  at: number,
  count: number,
  mode: GridOpMode
): string {
  const parts = splitCellRef(ref);
  if (!parts) {
    return ref;
  }
  let { row, col, colAbs, rowAbs } = parts;

  if (axis === "row") {
    if (rowAbs) {
      return joinCellRef(colAbs, rowAbs, row, col);
    }
    if (mode === "insert") {
      if (row >= at) {
        row += count;
      }
    } else if (row === at) {
      return "#REF!";
    } else if (row > at) {
      row -= count;
    }
  } else {
    if (colAbs) {
      return joinCellRef(colAbs, rowAbs, row, col);
    }
    if (mode === "insert") {
      if (col >= at) {
        col += count;
      }
    } else if (col === at) {
      return "#REF!";
    } else if (col > at) {
      col -= count;
    }
  }

  if (row < 0 || col < 0) {
    return "#REF!";
  }
  return joinCellRef(colAbs, rowAbs, row, col);
}

function shiftRangeToken(
  startRef: string,
  endRef: string | undefined,
  axis: GridAxis,
  at: number,
  count: number,
  mode: GridOpMode
): string {
  const shiftedStart = shiftRefAddress(startRef, axis, at, count, mode);
  if (shiftedStart === "#REF!") {
    return "#REF!";
  }
  if (!endRef) {
    return shiftedStart;
  }
  const shiftedEnd = shiftRefAddress(endRef, axis, at, count, mode);
  return `${shiftedStart}:${shiftedEnd}`;
}

export function shiftFormulaForGridOp(
  raw: string,
  axis: GridAxis,
  at: number,
  count: number,
  mode: GridOpMode,
  modifiedSheetName: string,
  formulaSheetName: string
): string {
  if (!raw.trim().startsWith("=")) {
    return raw;
  }

  let formula = raw;

  formula = formula.replace(CROSS_RANGE_REF, (match, quotedSheet, plainSheet, startRef, endRef) => {
    const sheetName = quotedSheet ?? plainSheet;
    if (sheetName.toLowerCase() !== modifiedSheetName.toLowerCase()) {
      return match;
    }
    const shifted = shiftRangeToken(startRef, endRef, axis, at, count, mode);
    if (shifted === "#REF!") {
      return "#REF!";
    }
    return quotedSheet ? `'${quotedSheet}'!${shifted}` : `${plainSheet}!${shifted}`;
  });

  if (formulaSheetName.toLowerCase() !== modifiedSheetName.toLowerCase()) {
    return formula;
  }

  formula = formula.replace(LOCAL_RANGE_REF, (_match, startRef, endRef) => {
    return shiftRangeToken(startRef, endRef, axis, at, count, mode);
  });

  return formula;
}

function shiftAddressInSheet(
  address: string,
  axis: GridAxis,
  at: number,
  count: number,
  mode: GridOpMode,
  rows: number,
  cols: number
): string | null {
  const rc = a1ToRowCol(address);
  if (!rc) {
    return address;
  }
  let { row, col } = rc;

  if (axis === "row") {
    if (mode === "insert") {
      if (row >= at) {
        row += count;
      }
    } else if (row === at) {
      return null;
    } else if (row > at) {
      row -= count;
    }
  } else if (mode === "insert") {
    if (col >= at) {
      col += count;
    }
  } else if (col === at) {
    return null;
  } else if (col > at) {
    col -= count;
  }

  const nextRows =
    axis === "row" && mode === "insert" ? rows + count : axis === "row" && mode === "delete" ? rows - count : rows;
  const nextCols =
    axis === "col" && mode === "insert" ? cols + count : axis === "col" && mode === "delete" ? cols - count : cols;
  if (row < 0 || col < 0 || row >= nextRows || col >= nextCols) {
    return null;
  }
  return rowColToA1(row, col);
}

function shiftStyledRecord<T>(
  record: Record<string, T> | undefined,
  axis: GridAxis,
  at: number,
  count: number,
  mode: GridOpMode,
  rows: number,
  cols: number
): Record<string, T> | undefined {
  if (!record) {
    return undefined;
  }
  const next: Record<string, T> = {};
  for (const [addr, value] of Object.entries(record)) {
    const shifted = shiftAddressInSheet(addr, axis, at, count, mode, rows, cols);
    if (shifted) {
      next[shifted] = value;
    }
  }
  return next;
}

function shiftMergedCells(
  merges: SheetMergeRange[] | undefined,
  axis: GridAxis,
  at: number,
  count: number,
  mode: GridOpMode,
  rows: number,
  cols: number
): SheetMergeRange[] | undefined {
  if (!merges?.length) {
    return merges;
  }
  const nextRows =
    axis === "row" && mode === "insert" ? rows + count : axis === "row" && mode === "delete" ? rows - count : rows;
  const nextCols =
    axis === "col" && mode === "insert" ? cols + count : axis === "col" && mode === "delete" ? cols - count : cols;
  const result: SheetMergeRange[] = [];

  for (const merge of merges) {
    const anchorRc = a1ToRowCol(merge.anchor);
    if (!anchorRc) {
      continue;
    }
    let { row, col } = anchorRc;
    let rowSpan = merge.rowSpan;
    let colSpan = merge.colSpan;

    if (axis === "row") {
      const endRow = row + rowSpan - 1;
      if (mode === "insert") {
        if (at <= row) {
          row += count;
        } else if (at <= endRow) {
          rowSpan += count;
        }
      } else if (at >= row && at <= endRow) {
        if (rowSpan === 1) {
          continue;
        }
        if (at === row) {
          row += 1;
          rowSpan -= 1;
        } else if (at === endRow) {
          rowSpan -= 1;
        } else {
          rowSpan -= 1;
        }
      } else if (at < row) {
        row -= count;
      }
    } else {
      const endCol = col + colSpan - 1;
      if (mode === "insert") {
        if (at <= col) {
          col += count;
        } else if (at <= endCol) {
          colSpan += count;
        }
      } else if (at >= col && at <= endCol) {
        if (colSpan === 1) {
          continue;
        }
        if (at === col) {
          col += 1;
          colSpan -= 1;
        } else if (at === endCol) {
          colSpan -= 1;
        } else {
          colSpan -= 1;
        }
      } else if (at < col) {
        col -= count;
      }
    }

    if (row < 0 || col < 0 || row >= nextRows || col >= nextCols) {
      continue;
    }
    rowSpan = Math.min(rowSpan, nextRows - row);
    colSpan = Math.min(colSpan, nextCols - col);
    if (rowSpan < 1 || colSpan < 1) {
      continue;
    }
    result.push({ anchor: rowColToA1(row, col), rowSpan, colSpan });
  }
  return result;
}

function shiftSizeArray(
  sizes: number[] | undefined,
  at: number,
  count: number,
  mode: GridOpMode,
  length: number
): number[] | undefined {
  if (!sizes?.length) {
    return sizes;
  }
  const next = sizes.slice(0, length);
  while (next.length < length) {
    next.push(next[next.length - 1] ?? 0);
  }
  if (mode === "insert") {
    const insertValues = Array.from({ length: count }, () => next[Math.max(0, at - 1)] ?? next[0] ?? 0);
    next.splice(at, 0, ...insertValues);
  } else {
    next.splice(at, count);
  }
  return next.slice(0, length);
}

function shiftSheetContents(
  contents: SheetValues,
  sheetName: string,
  modifiedSheetName: string,
  axis: GridAxis,
  at: number,
  count: number,
  mode: GridOpMode,
  rows: number,
  cols: number,
  moveAddresses: boolean
): SheetValues {
  const result: SheetValues = {};

  for (const [addr, raw] of Object.entries(contents)) {
    const shiftedRaw = shiftFormulaForGridOp(
      raw,
      axis,
      at,
      count,
      mode,
      modifiedSheetName,
      sheetName
    );
    if (!moveAddresses) {
      result[addr] = shiftedRaw;
      continue;
    }
    const shiftedAddr = shiftAddressInSheet(addr, axis, at, count, mode, rows, cols);
    if (shiftedAddr) {
      result[shiftedAddr] = shiftedRaw;
    }
  }
  return result;
}

function applyGridOpToTab(
  tab: SheetTabData,
  modifiedSheetName: string,
  axis: GridAxis,
  at: number,
  count: number,
  mode: GridOpMode,
  isModifiedSheet: boolean
): SheetTabData {
  const rows = tab.rows;
  const cols = tab.cols;
  const nextRows =
    axis === "row" && mode === "insert" ? rows + count : axis === "row" && mode === "delete" ? Math.max(1, rows - count) : rows;
  const nextCols =
    axis === "col" && mode === "insert" ? cols + count : axis === "col" && mode === "delete" ? Math.max(1, cols - count) : cols;

  return {
    ...tab,
    rows: nextRows,
    cols: nextCols,
    contents: shiftSheetContents(
      tab.contents,
      tab.name,
      modifiedSheetName,
      axis,
      at,
      count,
      mode,
      rows,
      cols,
      isModifiedSheet
    ),
    cellStyles: isModifiedSheet
      ? shiftStyledRecord(tab.cellStyles, axis, at, count, mode, rows, cols)
      : tab.cellStyles,
    mergedCells: isModifiedSheet
      ? shiftMergedCells(tab.mergedCells, axis, at, count, mode, rows, cols)
      : tab.mergedCells,
    rowHeights: isModifiedSheet
      ? shiftSizeArray(tab.rowHeights, at, count, mode, nextRows)
      : tab.rowHeights,
    columnWidths: isModifiedSheet
      ? shiftSizeArray(tab.columnWidths, at, count, mode, nextCols)
      : tab.columnWidths,
  };
}

export interface GridOperation {
  axis: GridAxis;
  mode: GridOpMode;
  at: number;
  count?: number;
}

export function applyGridOperation(
  workbook: SheetWorkbook,
  activeSheetIndex: number,
  operation: GridOperation
): SheetWorkbook {
  const count = operation.count ?? 1;
  const index = Math.min(Math.max(activeSheetIndex, 0), workbook.sheets.length - 1);
  const active = workbook.sheets[index];
  if (!active) {
    return workbook;
  }

  const maxAt =
    operation.axis === "row"
      ? operation.mode === "insert"
        ? active.rows
        : active.rows - 1
      : operation.mode === "insert"
        ? active.cols
        : active.cols - 1;
  const at = Math.min(Math.max(operation.at, 0), maxAt);

  if (operation.mode === "delete") {
    if (operation.axis === "row" && active.rows <= 1) {
      return workbook;
    }
    if (operation.axis === "col" && active.cols <= 1) {
      return workbook;
    }
  }

  const modifiedSheetName = active.name;
  const sheets = workbook.sheets.map((tab, tabIndex) =>
    applyGridOpToTab(tab, modifiedSheetName, operation.axis, at, count, operation.mode, tabIndex === index)
  );

  return { ...workbook, activeSheetIndex: index, sheets };
}

export function shiftSelectedCell(
  address: string,
  axis: GridAxis,
  at: number,
  count: number,
  mode: GridOpMode
): string {
  const rc = a1ToRowCol(address);
  if (!rc) {
    return address;
  }
  const shifted = shiftAddressInSheet(
    address,
    axis,
    at,
    count,
    mode,
    Number.MAX_SAFE_INTEGER,
    Number.MAX_SAFE_INTEGER
  );
  if (shifted) {
    return shifted;
  }
  if (mode === "insert" && axis === "row" && rc.row >= at) {
    return rowColToA1(rc.row + count, rc.col);
  }
  if (mode === "insert" && axis === "col" && rc.col >= at) {
    return rowColToA1(rc.row, rc.col + count);
  }
  return address;
}

export function runtimeMetaFromTab(tab: SheetTabData): SheetRuntimeMeta {
  return sheetTabToRuntimeMeta(tab);
}
