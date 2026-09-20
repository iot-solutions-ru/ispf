// Shared types, error sentinels and coercions for the sheet formula evaluator.
import { a1ToRowCol, rowColToA1, type SheetBounds } from "./sheetAddress";
import type { IspfFormulaContext } from "./sheetFormulaEngineContext";

export type SheetEvalValue = number | string | boolean;
export type SheetEvalResult = SheetEvalValue | null;

export const ERROR = {
  name: "#NAME?",
  ref: "#REF!",
  div0: "#DIV/0!",
  value: "#VALUE!",
  num: "#NUM!",
  cycle: "#CYCLE!",
  na: "#N/A",
} as const;

export function isSheetError(value: unknown): value is string {
  return (
    value === ERROR.name ||
    value === ERROR.ref ||
    value === ERROR.div0 ||
    value === ERROR.value ||
    value === ERROR.num ||
    value === ERROR.cycle ||
    value === ERROR.na
  );
}

export function expandRange(start: string, end: string): string[] | null {
  const a = a1ToRowCol(start.toUpperCase());
  const b = a1ToRowCol(end.toUpperCase());
  if (!a || !b) {
    return null;
  }
  const minRow = Math.min(a.row, b.row);
  const maxRow = Math.max(a.row, b.row);
  const minCol = Math.min(a.col, b.col);
  const maxCol = Math.max(a.col, b.col);
  const cells: string[] = [];
  for (let r = minRow; r <= maxRow; r++) {
    for (let c = minCol; c <= maxCol; c++) {
      cells.push(rowColToA1(r, c));
    }
  }
  return cells;
}

export interface SheetEvalEnvironment {
  /** Address is `A1` or `SheetName!A1`. Unqualified refs use defaultSheet when set. */
  getCell: (address: string) => SheetEvalResult;
  /** Cell containing the formula (`A1`); used by ROW/COLUMN. */
  originCell?: string;
  defaultSheet?: string;
  /** Grid size for whole-column (`D:D`) and whole-row (`5:5`) refs. */
  getSheetBounds?: (sheetName?: string) => SheetBounds;
  ispf: IspfFormulaContext;
}

/** One spreadsheet function: already-evaluated args (errors propagated by the dispatcher unless the function is error-tolerant). */
export type SheetFunction = (args: SheetEvalResult[], env: SheetEvalEnvironment) => SheetEvalResult;
export type SheetFunctionRegistry = Record<string, SheetFunction>;

export function coerceNumber(value: unknown): number {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === "boolean") {
    return value ? 1 : 0;
  }
  const num = Number.parseFloat(String(value ?? ""));
  return Number.isFinite(num) ? num : 0;
}

export function coerceBool(value: SheetEvalResult): boolean {
  if (typeof value === "boolean") {
    return value;
  }
  if (typeof value === "number") {
    return value !== 0;
  }
  return String(value ?? "").length > 0 && String(value).toLowerCase() !== "false";
}

export function numbersFromArgs(args: SheetEvalResult[]): number[] | string {
  const nums: number[] = [];
  for (const arg of args) {
    if (isSheetError(arg)) {
      return arg;
    }
    if (Array.isArray(arg)) {
      return ERROR.value;
    }
    nums.push(coerceNumber(arg));
  }
  return nums;
}
