import {
  FunctionArgumentType,
  FunctionPlugin,
  HyperFormula,
  type RawCellContent,
} from "hyperformula";
import { enGB } from "hyperformula/es/i18n/languages";
import type { SheetConfig } from "../../../types/dashboard";
import type { SheetMode } from "../../../types/dashboard";
import { a1ToRowCol, rowColToA1 } from "./sheetAddress";

export type SheetValues = Record<string, string>;

export interface IspfFormulaContext {
  bindingValues: Map<string, number | string | boolean>;
  tableColumnSums: Map<string, number>;
  histValues: Map<string, number>;
}

let ispfFormulaContext: IspfFormulaContext = {
  bindingValues: new Map(),
  tableColumnSums: new Map(),
  histValues: new Map(),
};

export function setIspfFormulaContext(ctx: IspfFormulaContext): void {
  ispfFormulaContext = ctx;
}

export function bindingCacheKey(path: string, varName: string, field = "value"): string {
  return `${path}|${varName}|${field}`;
}

export function histCacheKey(path: string, varName: string, minutes: number): string {
  return `${path}|${varName}|${minutes}`;
}

class IspfFunctionPlugin extends FunctionPlugin {
  ispref(ast: unknown, state: unknown) {
    const node = ast as { args: unknown };
    return this.runFunction(node.args as never, state as never, this.metadata("ISPREF"), (...args: unknown[]) => {
      const path = String(args[0] ?? "");
      const varName = String(args[1] ?? "");
      const field = args[2] !== undefined ? String(args[2]) : "value";
      const v = ispfFormulaContext.bindingValues.get(bindingCacheKey(path, varName, field));
      return coerceToNumber(v);
    });
  }

  ispsum(ast: unknown, state: unknown) {
    const node = ast as { args: unknown };
    return this.runFunction(node.args as never, state as never, this.metadata("ISPSUM"), (...args: unknown[]) => {
      const tableVar = String(args[0] ?? "");
      const column = String(args[1] ?? "");
      return ispfFormulaContext.tableColumnSums.get(`${tableVar}|${column}`) ?? 0;
    });
  }

  isphist(ast: unknown, state: unknown) {
    const node = ast as { args: unknown };
    return this.runFunction(node.args as never, state as never, this.metadata("ISPHIST"), (...args: unknown[]) => {
      const path = String(args[0] ?? "");
      const varName = String(args[1] ?? "");
      const minutes = args[2] !== undefined ? Number(args[2]) : 5;
      const key = histCacheKey(path, varName, minutes);
      const v = ispfFormulaContext.histValues.get(key);
      if (v !== undefined) {
        return v;
      }
      const fallback = ispfFormulaContext.bindingValues.get(bindingCacheKey(path, varName, "value"));
      return coerceToNumber(fallback);
    });
  }
}

IspfFunctionPlugin.implementedFunctions = {
  ISPREF: {
    method: "ispref",
    parameters: [
      { argumentType: FunctionArgumentType.STRING },
      { argumentType: FunctionArgumentType.STRING },
      { argumentType: FunctionArgumentType.STRING, optionalArg: true },
    ],
  },
  ISPSUM: {
    method: "ispsum",
    parameters: [
      { argumentType: FunctionArgumentType.STRING },
      { argumentType: FunctionArgumentType.STRING },
    ],
  },
  ISPHIST: {
    method: "isphist",
    parameters: [
      { argumentType: FunctionArgumentType.STRING },
      { argumentType: FunctionArgumentType.STRING },
      { argumentType: FunctionArgumentType.NUMBER, optionalArg: true },
    ],
  },
};

const DEFAULT_FORMULA_LANGUAGE = "enGB";
let formulaLanguageRegistered = false;
let ispfFunctionsRegistered = false;

function ensureFormulaLanguageRegistered(): void {
  if (formulaLanguageRegistered) {
    return;
  }
  if (!HyperFormula.getRegisteredLanguagesCodes().includes(DEFAULT_FORMULA_LANGUAGE)) {
    HyperFormula.registerLanguage(DEFAULT_FORMULA_LANGUAGE, enGB);
  }
  formulaLanguageRegistered = true;
}

function ensureIspfFunctionsRegistered(): void {
  if (ispfFunctionsRegistered) {
    return;
  }
  if (!HyperFormula.getFunctionPlugin("ISPREF")) {
    HyperFormula.registerFunctionPlugin(IspfFunctionPlugin, {
      [DEFAULT_FORMULA_LANGUAGE]: {
        ISPREF: "ISPREF",
        ISPSUM: "ISPSUM",
        ISPHIST: "ISPHIST",
      },
    });
  }
  ispfFunctionsRegistered = true;
}

function coerceToNumber(value: unknown): number {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === "boolean") {
    return value ? 1 : 0;
  }
  const num = Number.parseFloat(String(value ?? ""));
  return Number.isFinite(num) ? num : 0;
}

export function rawToCellContent(raw: string): RawCellContent {
  const trimmed = raw.trim();
  if (trimmed === "") {
    return "";
  }
  if (trimmed.startsWith("=")) {
    return trimmed;
  }
  const num = Number.parseFloat(trimmed);
  if (Number.isFinite(num) && /^-?\d+(\.\d+)?$/.test(trimmed)) {
    return num;
  }
  return raw;
}

function seedContentForCell(
  addr: string,
  config: SheetConfig,
  contents: SheetValues
): string {
  if (contents[addr] !== undefined) {
    return contents[addr];
  }
  const cell = config.cells[addr];
  if (!cell) {
    return "";
  }
  if (cell.kind === "formula" && cell.expr) {
    return cell.expr.startsWith("=") ? cell.expr : `=${cell.expr}`;
  }
  if (cell.kind === "input" || cell.kind === "readonly") {
    return cell.default ?? "";
  }
  if (cell.kind === "label") {
    return cell.text ?? "";
  }
  return "";
}

function buildConfiguredGrid(
  config: SheetConfig,
  values: SheetValues,
  externalByAddr: Map<string, number | string | boolean>
): RawCellContent[][] {
  const grid: RawCellContent[][] = Array.from({ length: config.rows }, () =>
    Array.from({ length: config.cols }, () => null)
  );

  for (const [addr, cell] of Object.entries(config.cells)) {
    const rc = a1ToRowCol(addr);
    if (!rc || rc.row >= config.rows || rc.col >= config.cols) {
      continue;
    }
    if (cell.kind === "formula" && cell.expr) {
      const expr = cell.expr.trim();
      grid[rc.row][rc.col] = expr.startsWith("=") ? expr : `=${expr}`;
    } else if (cell.kind === "input") {
      grid[rc.row][rc.col] = rawToCellContent(values[addr] ?? cell.default ?? "");
    } else if (cell.kind === "binding") {
      const ext = externalByAddr.get(addr);
      if (ext !== undefined) {
        grid[rc.row][rc.col] = typeof ext === "number" ? ext : String(ext);
      }
    } else if (cell.kind === "readonly" && cell.default !== undefined) {
      grid[rc.row][rc.col] = rawToCellContent(cell.default);
    }
  }
  return grid;
}

function buildFreeGrid(
  config: SheetConfig,
  contents: SheetValues,
  externalByAddr: Map<string, number | string | boolean>,
  bindingAddresses: Set<string>
): RawCellContent[][] {
  const grid: RawCellContent[][] = Array.from({ length: config.rows }, () =>
    Array.from({ length: config.cols }, () => null)
  );

  for (let r = 0; r < config.rows; r++) {
    for (let c = 0; c < config.cols; c++) {
      const addr = rowColToA1(r, c);
      const seed = config.cells[addr];
      if (seed?.kind === "binding" || bindingAddresses.has(addr)) {
        const ext = externalByAddr.get(addr);
        if (ext !== undefined) {
          grid[r][c] = typeof ext === "number" ? ext : String(ext);
        }
        continue;
      }
      const raw = seedContentForCell(addr, config, contents);
      if (raw) {
        grid[r][c] = rawToCellContent(raw);
      }
    }
  }
  return grid;
}

export class SheetFormulaEngine {
  private readonly hf: HyperFormula;
  private readonly sheetId: number;
  private readonly config: SheetConfig;
  private readonly mode: SheetMode;
  private readonly cellContents: SheetValues;
  private readonly bindingAddresses: Set<string>;
  private selectionAnchor: string | null = null;

  constructor(
    config: SheetConfig,
    mode: SheetMode,
    initialContents: SheetValues,
    externalByAddr: Map<string, number | string | boolean> = new Map()
  ) {
    this.config = config;
    this.mode = mode;
    this.cellContents = { ...initialContents };
    this.bindingAddresses = new Set(
      Object.entries(config.cells)
        .filter(([, cell]) => cell.kind === "binding")
        .map(([addr]) => addr)
    );

    const grid =
      mode === "free"
        ? buildFreeGrid(config, initialContents, externalByAddr, this.bindingAddresses)
        : buildConfiguredGrid(config, initialContents, externalByAddr);

    ensureFormulaLanguageRegistered();
    ensureIspfFunctionsRegistered();
    this.hf = HyperFormula.buildFromArray(grid, {
      licenseKey: "gpl-v3",
      language: DEFAULT_FORMULA_LANGUAGE,
    });
    const sheetName = this.hf.getSheetNames()[0] ?? "Sheet1";
    this.sheetId = this.hf.getSheetId(sheetName) ?? 0;
  }

  isBindingCell(address: string): boolean {
    return this.bindingAddresses.has(address);
  }

  setCellContent(address: string, raw: string): void {
    if (this.isBindingCell(address)) {
      return;
    }
    const rc = a1ToRowCol(address);
    if (!rc) {
      return;
    }
    const trimmed = raw.trim();
    if (trimmed === "") {
      delete this.cellContents[address];
      this.hf.setCellContents({ sheet: this.sheetId, row: rc.row, col: rc.col }, [[""]]);
      return;
    }
    this.cellContents[address] = raw;
    this.hf.setCellContents(
      { sheet: this.sheetId, row: rc.row, col: rc.col },
      [[rawToCellContent(raw)]]
    );
  }

  setInputValue(address: string, raw: string): void {
    this.setCellContent(address, raw);
  }

  setExternalValue(address: string, value: number | string | boolean): void {
    if (!this.isBindingCell(address) && this.mode !== "free") {
      const cell = this.config.cells[address];
      if (!cell || cell.kind !== "binding") {
        return;
      }
    }
    const rc = a1ToRowCol(address);
    if (!rc) {
      return;
    }
    const content: RawCellContent =
      typeof value === "number" ? value : typeof value === "boolean" ? (value ? 1 : 0) : value;
    this.hf.setCellContents({ sheet: this.sheetId, row: rc.row, col: rc.col }, [[content]]);
  }

  getCellValue(address: string): unknown {
    const rc = a1ToRowCol(address);
    if (!rc) {
      return "";
    }
    return this.hf.getCellValue({ sheet: this.sheetId, row: rc.row, col: rc.col });
  }

  getCellEditContent(address: string): string {
    if (this.cellContents[address] !== undefined) {
      return this.cellContents[address];
    }
    const rc = a1ToRowCol(address);
    if (!rc) {
      return "";
    }
    try {
      const formula = this.hf.getCellFormula({
        sheet: this.sheetId,
        row: rc.row,
        col: rc.col,
      });
      if (formula) {
        return formula.startsWith("=") ? formula : `=${formula}`;
      }
    } catch {
      // not a formula cell
    }
    const val = this.getCellValue(address);
    if (val === null || val === undefined) {
      return "";
    }
    return String(val);
  }

  collectCellContents(): SheetValues {
    if (this.mode === "free") {
      return { ...this.cellContents };
    }
    return this.collectInputValues();
  }

  collectInputValues(): SheetValues {
    const result: SheetValues = {};
    for (const [addr, cell] of Object.entries(this.config.cells)) {
      if (cell.kind === "input") {
        result[addr] = this.getCellEditContent(addr);
      }
    }
    return result;
  }

  setSelectionAnchor(address: string | null): void {
    this.selectionAnchor = address;
  }

  getSelectionAnchor(): string | null {
    return this.selectionAnchor;
  }

  copySelection(): void {
    if (!this.selectionAnchor) {
      return;
    }
    const rc = a1ToRowCol(this.selectionAnchor);
    if (!rc) {
      return;
    }
    const cell = { sheet: this.sheetId, col: rc.col, row: rc.row };
    this.hf.copy({ start: cell, end: cell });
  }

  pasteAt(address: string): void {
    const rc = a1ToRowCol(address);
    if (!rc) {
      return;
    }
    this.hf.paste({ sheet: this.sheetId, col: rc.col, row: rc.row });
    this.syncContentsFromSheet();
  }

  undo(): boolean {
    if (!this.hf.isThereSomethingToUndo()) {
      return false;
    }
    this.hf.undo();
    this.syncContentsFromSheet();
    return true;
  }

  redo(): boolean {
    if (!this.hf.isThereSomethingToRedo()) {
      return false;
    }
    this.hf.redo();
    this.syncContentsFromSheet();
    return true;
  }

  exportCsv(): string {
    const lines: string[] = [];
    for (let r = 0; r < this.config.rows; r++) {
      const row: string[] = [];
      for (let c = 0; c < this.config.cols; c++) {
        const addr = rowColToA1(r, c);
        const val = this.getCellValue(addr);
        const text = formatCsvCell(val);
        row.push(text);
      }
      lines.push(row.join(","));
    }
    return lines.join("\n");
  }

  mergeRegionContents(regionContents: SheetValues): void {
    for (const [addr, raw] of Object.entries(regionContents)) {
      if (!this.isBindingCell(addr) && raw) {
        this.setCellContent(addr, raw);
      }
    }
  }

  private syncContentsFromSheet(): void {
    for (let r = 0; r < this.config.rows; r++) {
      for (let c = 0; c < this.config.cols; c++) {
        const addr = rowColToA1(r, c);
        if (this.isBindingCell(addr)) {
          continue;
        }
        const edit = this.getCellEditContent(addr);
        if (edit.trim()) {
          this.cellContents[addr] = edit;
        } else {
          delete this.cellContents[addr];
        }
      }
    }
  }

  destroy(): void {
    this.hf.destroy();
  }
}

function formatCsvCell(val: unknown): string {
  if (val === null || val === undefined) {
    return "";
  }
  if (typeof val === "object" && val !== null && "type" in val) {
    return String((val as { value?: string }).value ?? "#ERROR");
  }
  const text = String(val);
  if (text.includes(",") || text.includes('"') || text.includes("\n")) {
    return `"${text.replace(/"/g, '""')}"`;
  }
  return text;
}

export function createSheetFormulaEngine(
  config: SheetConfig,
  mode: SheetMode,
  initialContents: SheetValues,
  externalByAddr?: Map<string, number | string | boolean>
): SheetFormulaEngine {
  return new SheetFormulaEngine(config, mode, initialContents, externalByAddr);
}

export function regionContentsFromRows(
  config: SheetConfig,
  rows: Array<Record<string, unknown>>
): SheetValues {
  const region = config.dataRegion;
  if (!region) {
    return {};
  }
  const result: SheetValues = {};
  rows.forEach((row, ri) => {
    region.columnFields.forEach((field, ci) => {
      const addr = rowColToA1(region.startRow + ri, region.startCol + ci);
      const val = row[field];
      if (val !== undefined && val !== null) {
        result[addr] = String(val);
      }
    });
  });
  return result;
}
