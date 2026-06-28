import { a1ToRowCol, DEFAULT_SHEET_BOUNDS, resolveRangeEndpoints, rowColToA1, type SheetBounds } from "./sheetAddress";
import { normalizeFormulaSyntax, normalizeFunctionName } from "./sheetFormulaNormalize";
import {
  buildMatrixFromRangeRefs,
  countBlank,
  countNonBlank,
  countNumeric,
  excelAverageif,
  excelCountif,
  excelHlookup,
  excelIndex,
  excelMatch,
  excelSumif,
  excelVlookup,
  type SheetMatrix,
} from "./sheetExcelFunctions";
import {
  excelDate,
  excelDay,
  excelDays,
  excelDatedif,
  excelEdate,
  excelEomonth,
  excelHour,
  excelMinute,
  excelMonth,
  excelNetworkdays,
  excelNowSerial,
  excelRand,
  excelRandBetween,
  excelSecond,
  excelTime,
  excelTodaySerial,
  excelWeekday,
  excelWorkday,
  excelYear,
} from "./sheetDateFunctions";
import {
  excelAverageifs,
  excelCountifs,
  excelIfs,
  excelMedian,
  excelRoundDown,
  excelRoundUp,
  excelStdevS,
  excelSubtotal,
  excelSumifs,
  excelSumproduct,
  excelTextjoin,
  excelXlookup,
} from "./sheetExcelFunctionsReport";
import {
  excelChoose,
  excelDatevalue,
  excelExact,
  excelFind,
  excelFv,
  excelIfna,
  excelIrr,
  excelLarge,
  excelLn,
  excelLog,
  excelLog10,
  excelMround,
  excelNper,
  excelNpv,
  excelPercentile,
  excelPmt,
  excelPv,
  excelQuartile,
  excelRank,
  excelRate,
  excelReplace,
  excelSearch,
  excelSign,
  excelSmall,
  excelSubstitute,
  excelSwitch,
  excelTimevalue,
  excelTrunc,
  excelValue,
  excelVarP,
  excelVarS,
  excelWeeknum,
  excelYearfrac,
  numbersFromFlat,
} from "./sheetExcelFunctionsPhaseB";
import {
  bindingCacheKey,
  histCacheKey,
  type IspfFormulaContext,
} from "./sheetFormulaEngineContext";
import { parseQualifiedCellRef } from "./sheetWorkbook";

export type SheetEvalValue = number | string | boolean;
export type SheetEvalResult = SheetEvalValue | null;

const ERROR = {
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

type Token =
  | { kind: "num"; value: number }
  | { kind: "str"; value: string }
  | {
      kind: "cell";
      value: string;
      sheet?: string;
      columnOnly?: boolean;
      rowOnly?: boolean;
    }
  | { kind: "ident"; value: string }
  | { kind: "op"; value: string }
  | { kind: "lparen" }
  | { kind: "rparen" }
  | { kind: "comma" }
  | { kind: "colon" }
  | { kind: "eof" };

type RangeEndpoint = {
  value: string;
  columnOnly: boolean;
  rowOnly: boolean;
};

function readRangeEndpoint(input: string, start: number): { endpoint: RangeEndpoint; next: number } | string {
  let i = start;
  let letters = "";
  while (i < input.length && /[A-Z$]/i.test(input[i])) {
    letters += input[i];
    i++;
  }
  let digits = "";
  while (i < input.length && /[0-9]/.test(input[i])) {
    digits += input[i];
    i++;
  }
  const col = letters.replace(/\$/g, "").toUpperCase();
  if (col && digits) {
    return { endpoint: { value: `${col}${digits}`, columnOnly: false, rowOnly: false }, next: i };
  }
  if (col && !digits) {
    return { endpoint: { value: col, columnOnly: true, rowOnly: false }, next: i };
  }
  if (!col && digits) {
    return { endpoint: { value: digits, columnOnly: false, rowOnly: true }, next: i };
  }
  return ERROR.ref;
}

function tokenize(input: string): Token[] | string {
  const tokens: Token[] = [];
  let i = 0;
  while (i < input.length) {
    const ch = input[i];
    if (/\s/.test(ch)) {
      i++;
      continue;
    }
    if (ch === "(") {
      tokens.push({ kind: "lparen" });
      i++;
      continue;
    }
    if (ch === ")") {
      tokens.push({ kind: "rparen" });
      i++;
      continue;
    }
    if (ch === "," || ch === ";") {
      tokens.push({ kind: "comma" });
      i++;
      continue;
    }
    if (ch === ":") {
      tokens.push({ kind: "colon" });
      i++;
      continue;
    }
    if (ch === '"' || ch === "'") {
      const quote = ch;
      i++;
      let value = "";
      while (i < input.length && input[i] !== quote) {
        if (input[i] === quote && input[i + 1] === quote) {
          value += quote;
          i += 2;
          continue;
        }
        value += input[i];
        i++;
      }
      if (i >= input.length) {
        return ERROR.value;
      }
      i++;
      if (input[i] === "!") {
        i++;
        const cellPart = readRangeEndpoint(input, i);
        if (typeof cellPart === "string") {
          return cellPart;
        }
        i = cellPart.next;
        tokens.push({
          kind: "cell",
          value: cellPart.endpoint.value,
          sheet: value,
          columnOnly: cellPart.endpoint.columnOnly,
          rowOnly: cellPart.endpoint.rowOnly,
        });
        continue;
      }
      tokens.push({ kind: "str", value });
      continue;
    }
    if (ch === "&") {
      tokens.push({ kind: "op", value: "&" });
      i++;
      continue;
    }
    if (">=<=".includes(ch) || "<>".includes(ch) || "+-*/=".includes(ch)) {
      let op = ch;
      const two = input.slice(i, i + 2);
      if (two === ">=" || two === "<=" || two === "<>") {
        op = two;
        i += 2;
      } else {
        i++;
      }
      tokens.push({ kind: "op", value: op });
      continue;
    }
    if (/[0-9.]/.test(ch) || (ch === "-" && /[0-9]/.test(input[i + 1] ?? ""))) {
      let num = ch;
      i++;
      while (i < input.length && /[0-9.]/.test(input[i])) {
        num += input[i];
        i++;
      }
      const parsed = Number.parseFloat(num);
      if (!Number.isFinite(parsed)) {
        return ERROR.value;
      }
      tokens.push({ kind: "num", value: parsed });
      continue;
    }
    if (/[A-Za-z_А-ЯЁа-яё]/.test(ch)) {
      let ident = ch;
      i++;
      while (i < input.length && /[A-Za-z0-9_.А-ЯЁа-яё ]/.test(input[i])) {
        ident += input[i];
        i++;
      }
      if (input[i] === "!") {
        const sheetName = ident.trim();
        i++;
        const cellPart = readRangeEndpoint(input, i);
        if (typeof cellPart === "string") {
          return cellPart;
        }
        i = cellPart.next;
        tokens.push({
          kind: "cell",
          value: cellPart.endpoint.value,
          sheet: sheetName,
          columnOnly: cellPart.endpoint.columnOnly,
          rowOnly: cellPart.endpoint.rowOnly,
        });
        continue;
      }
      const upper = ident.trim().toUpperCase();
      if (/^[A-Z]+\d+$/.test(upper)) {
        tokens.push({ kind: "cell", value: upper });
      } else {
        tokens.push({ kind: "ident", value: upper });
      }
      continue;
    }
    return ERROR.value;
  }
  tokens.push({ kind: "eof" });
  return tokens;
}

class Parser {
  private pos = 0;

  constructor(
    private readonly tokens: Token[],
    private readonly env: SheetEvalEnvironment
  ) {}

  parse(): SheetEvalResult {
    const value = this.parseCompare();
    if (!this.match("eof")) {
      return ERROR.value;
    }
    return value;
  }

  private parseCompare(): SheetEvalResult {
    const left = this.parseConcat();
    if (isSheetError(left)) {
      return left;
    }
    const tok = this.peek();
    if (tok.kind === "op" && ["=", "<>", ">", "<", ">=", "<="].includes(tok.value)) {
      this.pos++;
      const right = this.parseConcat();
      if (isSheetError(right)) {
        return right;
      }
      const ln = coerceNumber(left);
      const rn = coerceNumber(right);
      if (typeof left === "string" || typeof right === "string") {
        const ls = String(left);
        const rs = String(right);
        switch (tok.value) {
          case "=":
            return ls === rs;
          case "<>":
            return ls !== rs;
          default:
            return ERROR.value;
        }
      }
      switch (tok.value) {
        case ">":
          return ln > rn;
        case "<":
          return ln < rn;
        case ">=":
          return ln >= rn;
        case "<=":
          return ln <= rn;
        case "=":
          return ln === rn;
        case "<>":
          return ln !== rn;
        default:
          return ERROR.value;
      }
    }
    return left;
  }

  private parseConcat(): SheetEvalResult {
    let value = this.parseAdd();
    while (this.match("op", "&")) {
      const right = this.parseAdd();
      if (isSheetError(value) || isSheetError(right)) {
        return isSheetError(value) ? value : right;
      }
      value = String(value ?? "") + String(right ?? "");
    }
    return value;
  }

  private parseAdd(): SheetEvalResult {
    let value = this.parseMul();
    while (true) {
      const tok = this.peek();
      if (tok.kind !== "op" || (tok.value !== "+" && tok.value !== "-")) {
        break;
      }
      this.pos++;
      const right = this.parseMul();
      if (isSheetError(value) || isSheetError(right)) {
        return isSheetError(value) ? value : right;
      }
      const ln = coerceNumber(value);
      const rn = coerceNumber(right);
      value = tok.value === "+" ? ln + rn : ln - rn;
    }
    return value;
  }

  private parseMul(): SheetEvalResult {
    let value = this.parseUnary();
    while (true) {
      const tok = this.peek();
      if (tok.kind !== "op" || (tok.value !== "*" && tok.value !== "/")) {
        break;
      }
      this.pos++;
      const right = this.parseUnary();
      if (isSheetError(value) || isSheetError(right)) {
        return isSheetError(value) ? value : right;
      }
      const ln = coerceNumber(value);
      const rn = coerceNumber(right);
      if (tok.value === "/") {
        if (rn === 0) {
          return ERROR.div0;
        }
        value = ln / rn;
      } else {
        value = ln * rn;
      }
    }
    return value;
  }

  private parseUnary(): SheetEvalResult {
    const tok = this.peek();
    if (tok.kind === "op" && (tok.value === "+" || tok.value === "-")) {
      this.pos++;
      const inner = this.parseUnary();
      if (isSheetError(inner)) {
        return inner;
      }
      const n = coerceNumber(inner);
      return tok.value === "-" ? -n : n;
    }
    return this.parsePrimary();
  }

  private parsePrimary(): SheetEvalResult {
    const tok = this.peek();
    if (tok.kind === "num") {
      this.pos++;
      return tok.value;
    }
    if (tok.kind === "str") {
      this.pos++;
      return tok.value;
    }
    if (tok.kind === "cell") {
      this.pos++;
      const next = this.peek();
      if (next.kind === "colon") {
        return ERROR.ref;
      }
      if (tok.columnOnly || tok.rowOnly) {
        return ERROR.ref;
      }
      const value = this.env.getCell(this.cellRefFromToken(tok));
      if (isSheetError(value)) {
        return value;
      }
      return value ?? 0;
    }
    if (tok.kind === "ident") {
      if (tok.value === "TRUE") {
        this.pos++;
        return true;
      }
      if (tok.value === "FALSE") {
        this.pos++;
        return false;
      }
      return this.parseFunction(tok.value);
    }
    if (tok.kind === "lparen") {
      this.pos++;
      const inner = this.parseCompare();
      if (!this.match("rparen")) {
        return ERROR.value;
      }
      return inner;
    }
    return ERROR.value;
  }

  private parseFunction(name: string): SheetEvalResult {
    this.pos++;
    if (!this.match("lparen")) {
      return ERROR.name;
    }
    const fn = normalizeFunctionName(name);
    let result: SheetEvalResult;
    switch (fn) {
      case "VLOOKUP":
        result = this.parseVlookupArgs();
        break;
      case "HLOOKUP":
        result = this.parseHlookupArgs();
        break;
      case "INDEX":
        result = this.parseIndexArgs();
        break;
      case "MATCH":
        result = this.parseMatchArgs();
        break;
      case "SUMIF":
        result = this.parseSumifArgs();
        break;
      case "COUNTIF":
        result = this.parseCountifArgs();
        break;
      case "AVERAGEIF":
        result = this.parseAverageifArgs();
        break;
      case "XLOOKUP":
        result = this.parseXlookupArgs();
        break;
      case "SUMIFS":
        result = this.parseSumifsArgs();
        break;
      case "COUNTIFS":
        result = this.parseCountifsArgs();
        break;
      case "AVERAGEIFS":
        result = this.parseAverageifsArgs();
        break;
      case "IFS":
        result = this.parseIfsArgs();
        break;
      case "TEXTJOIN":
        result = this.parseTextjoinArgs();
        break;
      case "SUBTOTAL":
        result = this.parseSubtotalArgs();
        break;
      case "SUMPRODUCT":
        result = this.parseSumproductArgs();
        break;
      case "ROW":
        result = this.parseRowArgs();
        break;
      case "COLUMN":
        result = this.parseColumnArgs();
        break;
      case "ROWS":
        result = this.parseRowsArgs();
        break;
      case "COLUMNS":
        result = this.parseColumnsArgs();
        break;
      default:
        result = this.parseGenericFunctionArgs(name);
        break;
    }
    if (!this.match("rparen")) {
      return ERROR.value;
    }
    return result;
  }

  private parseGenericFunctionArgs(name: string): SheetEvalResult {
    const args: SheetEvalResult[] = [];
    if (!this.check("rparen")) {
      do {
        const rangeArg = this.tryParseRangeArg();
        if (rangeArg !== undefined) {
          if (typeof rangeArg === "string" && isSheetError(rangeArg)) {
            args.push(rangeArg);
          } else if (Array.isArray(rangeArg)) {
            args.push(...rangeArg);
          }
        } else {
          args.push(this.parseCompare());
        }
      } while (this.match("comma"));
    }
    return invokeFunction(name, args, this.env);
  }

  private parseVlookupArgs(): SheetEvalResult {
    const lookup = this.parseCompare();
    if (isSheetError(lookup) || !this.match("comma")) {
      return ERROR.value;
    }
    const matrix = this.requireRangeMatrix();
    if (typeof matrix === "string") {
      return matrix;
    }
    if (!this.match("comma")) {
      return ERROR.value;
    }
    const colIndex = Math.trunc(coerceNumber(this.parseCompare()));
    let approximate = true;
    if (this.match("comma")) {
      approximate = coerceBool(this.parseCompare());
    }
    return excelVlookup(lookup, matrix, colIndex, approximate);
  }

  private parseHlookupArgs(): SheetEvalResult {
    const lookup = this.parseCompare();
    if (isSheetError(lookup) || !this.match("comma")) {
      return ERROR.value;
    }
    const matrix = this.requireRangeMatrix();
    if (typeof matrix === "string") {
      return matrix;
    }
    if (!this.match("comma")) {
      return ERROR.value;
    }
    const rowIndex = Math.trunc(coerceNumber(this.parseCompare()));
    let approximate = true;
    if (this.match("comma")) {
      approximate = coerceBool(this.parseCompare());
    }
    return excelHlookup(lookup, matrix, rowIndex, approximate);
  }

  private parseIndexArgs(): SheetEvalResult {
    const matrix = this.requireRangeMatrix();
    if (typeof matrix === "string") {
      return matrix;
    }
    if (!this.match("comma")) {
      return ERROR.value;
    }
    const rowNum = Math.trunc(coerceNumber(this.parseCompare()));
    let colNum: number | undefined;
    if (this.match("comma")) {
      colNum = Math.trunc(coerceNumber(this.parseCompare()));
    }
    return excelIndex(matrix, rowNum, colNum);
  }

  private parseMatchArgs(): SheetEvalResult {
    const lookup = this.parseCompare();
    if (isSheetError(lookup) || !this.match("comma")) {
      return ERROR.value;
    }
    const rangeValues = this.requireRangeValues();
    if (typeof rangeValues === "string") {
      return rangeValues;
    }
    let matchType = 0;
    if (this.match("comma")) {
      matchType = Math.trunc(coerceNumber(this.parseCompare()));
    }
    return excelMatch(lookup, rangeValues, matchType);
  }

  private parseSumifArgs(): SheetEvalResult {
    const rangeValues = this.requireRangeValues();
    if (typeof rangeValues === "string") {
      return rangeValues;
    }
    if (!this.match("comma")) {
      return ERROR.value;
    }
    const criteria = this.parseCompare();
    let sumRange: SheetEvalResult[] | undefined;
    if (this.match("comma")) {
      const sr = this.tryParseRangeArg();
      if (sr && typeof sr !== "string") {
        sumRange = sr;
      }
    }
    return excelSumif(rangeValues, criteria, sumRange);
  }

  private parseCountifArgs(): SheetEvalResult {
    const rangeValues = this.requireRangeValues();
    if (typeof rangeValues === "string") {
      return rangeValues;
    }
    if (!this.match("comma")) {
      return ERROR.value;
    }
    const criteria = this.parseCompare();
    return excelCountif(rangeValues, criteria);
  }

  private parseAverageifArgs(): SheetEvalResult {
    const rangeValues = this.requireRangeValues();
    if (typeof rangeValues === "string") {
      return rangeValues;
    }
    if (!this.match("comma")) {
      return ERROR.value;
    }
    const criteria = this.parseCompare();
    let avgRange: SheetEvalResult[] | undefined;
    if (this.match("comma")) {
      const sr = this.tryParseRangeArg();
      if (sr && typeof sr !== "string") {
        avgRange = sr;
      }
    }
    return excelAverageif(rangeValues, criteria, avgRange);
  }

  private parseXlookupArgs(): SheetEvalResult {
    const lookup = this.parseCompare();
    if (isSheetError(lookup) || !this.match("comma")) {
      return ERROR.value;
    }
    const lookupArray = this.requireRangeValues();
    if (typeof lookupArray === "string") {
      return lookupArray;
    }
    if (!this.match("comma")) {
      return ERROR.value;
    }
    const returnArray = this.requireRangeValues();
    if (typeof returnArray === "string") {
      return returnArray;
    }
    let ifNotFound: SheetEvalResult = "#N/A";
    let matchMode = 0;
    let searchMode = 1;
    if (this.match("comma")) {
      ifNotFound = this.parseCompare();
      if (this.match("comma")) {
        matchMode = Math.trunc(coerceNumber(this.parseCompare()));
        if (this.match("comma")) {
          searchMode = Math.trunc(coerceNumber(this.parseCompare()));
        }
      }
    }
    return excelXlookup(lookup, lookupArray, returnArray, ifNotFound, matchMode, searchMode);
  }

  private parseSumifsArgs(): SheetEvalResult {
    const sumRange = this.requireRangeValues();
    if (typeof sumRange === "string") {
      return sumRange;
    }
    const criteriaRanges: SheetEvalResult[][] = [];
    const criteria: SheetEvalResult[] = [];
    while (this.match("comma")) {
      const criteriaRange = this.tryParseRangeArg();
      if (!criteriaRange || typeof criteriaRange === "string") {
        return ERROR.value;
      }
      if (!this.match("comma")) {
        return ERROR.value;
      }
      criteriaRanges.push(criteriaRange);
      criteria.push(this.parseCompare());
    }
    if (criteriaRanges.length === 0) {
      return ERROR.value;
    }
    return excelSumifs(sumRange, criteriaRanges, criteria);
  }

  private parseCountifsArgs(): SheetEvalResult {
    const criteriaRanges: SheetEvalResult[][] = [];
    const criteria: SheetEvalResult[] = [];
    const firstRange = this.tryParseRangeArg();
    if (!firstRange || typeof firstRange === "string") {
      return firstRange ?? ERROR.ref;
    }
    criteriaRanges.push(firstRange);
    if (!this.match("comma")) {
      return ERROR.value;
    }
    criteria.push(this.parseCompare());
    while (this.match("comma")) {
      const criteriaRange = this.tryParseRangeArg();
      if (!criteriaRange || typeof criteriaRange === "string") {
        return ERROR.value;
      }
      if (!this.match("comma")) {
        return ERROR.value;
      }
      criteriaRanges.push(criteriaRange);
      criteria.push(this.parseCompare());
    }
    return excelCountifs(criteriaRanges, criteria);
  }

  private parseAverageifsArgs(): SheetEvalResult {
    const avgRange = this.requireRangeValues();
    if (typeof avgRange === "string") {
      return avgRange;
    }
    const criteriaRanges: SheetEvalResult[][] = [];
    const criteria: SheetEvalResult[] = [];
    while (this.match("comma")) {
      const criteriaRange = this.tryParseRangeArg();
      if (!criteriaRange || typeof criteriaRange === "string") {
        return ERROR.value;
      }
      if (!this.match("comma")) {
        return ERROR.value;
      }
      criteriaRanges.push(criteriaRange);
      criteria.push(this.parseCompare());
    }
    if (criteriaRanges.length === 0) {
      return ERROR.value;
    }
    return excelAverageifs(avgRange, criteriaRanges, criteria);
  }

  private parseIfsArgs(): SheetEvalResult {
    const pairs: SheetEvalResult[] = [];
    if (!this.check("rparen")) {
      do {
        pairs.push(this.parseCompare());
      } while (this.match("comma"));
    }
    if (pairs.length < 2 || pairs.length % 2 !== 0) {
      return ERROR.value;
    }
    return excelIfs(pairs);
  }

  private parseTextjoinArgs(): SheetEvalResult {
    const delimiter = this.parseCompare();
    if (isSheetError(delimiter) || !this.match("comma")) {
      return ERROR.value;
    }
    const ignoreEmpty = coerceBool(this.parseCompare());
    const parts: SheetEvalResult[] = [];
    while (this.match("comma")) {
      const rangeArg = this.tryParseRangeArg();
      if (rangeArg !== undefined) {
        if (typeof rangeArg === "string" && isSheetError(rangeArg)) {
          return rangeArg;
        }
        if (Array.isArray(rangeArg)) {
          parts.push(...rangeArg);
        }
      } else {
        parts.push(this.parseCompare());
      }
    }
    return excelTextjoin(delimiter, ignoreEmpty, parts);
  }

  private parseSubtotalArgs(): SheetEvalResult {
    const functionNum = Math.trunc(coerceNumber(this.parseCompare()));
    if (!this.match("comma")) {
      return ERROR.value;
    }
    const values: SheetEvalResult[] = [];
    do {
      const rangeArg = this.tryParseRangeArg();
      if (rangeArg !== undefined) {
        if (typeof rangeArg === "string" && isSheetError(rangeArg)) {
          return rangeArg;
        }
        if (Array.isArray(rangeArg)) {
          values.push(...rangeArg);
        }
      } else {
        values.push(this.parseCompare());
      }
    } while (this.match("comma"));
    return excelSubtotal(functionNum, values);
  }

  private parseSumproductArgs(): SheetEvalResult {
    const arrays: SheetEvalResult[][] = [];
    if (!this.check("rparen")) {
      do {
        const range = this.tryParseRangeArg();
        if (!range || typeof range === "string") {
          return range ?? ERROR.ref;
        }
        arrays.push(range);
      } while (this.match("comma"));
    }
    if (arrays.length === 0) {
      return ERROR.value;
    }
    return excelSumproduct(arrays);
  }

  private parseOptionalCellRowCol(): { row: number; col: number } | undefined {
    const tok = this.peek();
    if (tok.kind !== "cell") {
      return undefined;
    }
    this.pos++;
    return a1ToRowCol(tok.value.toUpperCase()) ?? undefined;
  }

  private originRowCol(): { row: number; col: number } | string {
    const origin = this.env.originCell?.toUpperCase();
    if (!origin) {
      return ERROR.value;
    }
    const rc = a1ToRowCol(origin);
    return rc ?? ERROR.value;
  }

  private parseRowArgs(): SheetEvalResult {
    const ref = this.parseOptionalCellRowCol();
    if (ref) {
      return ref.row + 1;
    }
    const origin = this.originRowCol();
    return typeof origin === "string" ? origin : origin.row + 1;
  }

  private parseColumnArgs(): SheetEvalResult {
    const ref = this.parseOptionalCellRowCol();
    if (ref) {
      return ref.col + 1;
    }
    const origin = this.originRowCol();
    return typeof origin === "string" ? origin : origin.col + 1;
  }

  private parseRangeDimension(which: "rows" | "cols"): SheetEvalResult {
    const matrix = this.tryParseRangeMatrix();
    if (matrix && typeof matrix !== "string") {
      return which === "rows" ? matrix.length : (matrix[0]?.length ?? 0);
    }
    if (this.parseOptionalCellRowCol()) {
      return 1;
    }
    return ERROR.ref;
  }

  private parseRowsArgs(): SheetEvalResult {
    return this.parseRangeDimension("rows");
  }

  private parseColumnsArgs(): SheetEvalResult {
    return this.parseRangeDimension("cols");
  }

  private requireRangeMatrix(): SheetMatrix | string {
    const matrix = this.tryParseRangeMatrix();
    if (!matrix) {
      return ERROR.ref;
    }
    return matrix;
  }

  private requireRangeValues(): SheetEvalResult[] | string {
    const range = this.tryParseRangeArg();
    if (!range || typeof range === "string") {
      return range ?? ERROR.ref;
    }
    return range;
  }

  private cellRefFromToken(token: Extract<Token, { kind: "cell" }>): string {
    if (token.sheet) {
      return `${token.sheet}!${token.value}`;
    }
    return token.value;
  }

  private sheetBoundsFor(sheetName?: string | null): SheetBounds {
    return this.env.getSheetBounds?.(sheetName ?? this.env.defaultSheet) ?? DEFAULT_SHEET_BOUNDS;
  }

  private resolveTokenRange(
    startTok: Extract<Token, { kind: "cell" }>,
    endTok: Extract<Token, { kind: "cell" }>
  ): { startRef: string; endRef: string } | string {
    const sheetName = startTok.sheet ?? endTok.sheet ?? this.env.defaultSheet;
    const resolved = resolveRangeEndpoints(
      startTok.value,
      endTok.value,
      this.sheetBoundsFor(sheetName),
      {
        startColumnOnly: startTok.columnOnly,
        endColumnOnly: endTok.columnOnly,
        startRowOnly: startTok.rowOnly,
        endRowOnly: endTok.rowOnly,
      }
    );
    if (!resolved) {
      return ERROR.ref;
    }
    const sheet = startTok.sheet ?? endTok.sheet;
    return {
      startRef: sheet ? `${sheet}!${resolved.start}` : resolved.start,
      endRef: sheet ? `${sheet}!${resolved.end}` : resolved.end,
    };
  }

  private rangeEndToken(index: number): Extract<Token, { kind: "cell" }> | null {
    const tok = this.tokens[index];
    if (!tok) {
      return null;
    }
    if (tok.kind === "cell") {
      return tok;
    }
    if (tok.kind === "ident" && /^[A-Z]+$/.test(tok.value)) {
      return { kind: "cell", value: tok.value, columnOnly: true, rowOnly: false };
    }
    if (tok.kind === "ident" && /^[A-Z]+\d+$/.test(tok.value)) {
      return { kind: "cell", value: tok.value, columnOnly: false, rowOnly: false };
    }
    if (tok.kind === "ident" && /^\d+$/.test(tok.value)) {
      return { kind: "cell", value: tok.value, columnOnly: false, rowOnly: true };
    }
    return null;
  }

  private tryParseBareColumnRange(): SheetEvalResult[] | string | undefined {
    const startTok = this.peek();
    if (startTok.kind !== "ident" || !/^[A-Z]+$/.test(startTok.value)) {
      return undefined;
    }
    if (this.tokens[this.pos + 1]?.kind !== "colon") {
      return undefined;
    }
    const endTok = this.tokens[this.pos + 2];
    if (!endTok || endTok.kind !== "ident" || !/^[A-Z]+$/.test(endTok.value)) {
      return undefined;
    }
    this.pos += 3;
    const resolved = resolveRangeEndpoints(
      startTok.value,
      endTok.value,
      this.sheetBoundsFor(this.env.defaultSheet),
      { startColumnOnly: true, endColumnOnly: true }
    );
    if (!resolved) {
      return ERROR.ref;
    }
    return this.readRangeValues(resolved.start, resolved.end);
  }

  private tryParseRangeMatrix(): SheetMatrix | string | undefined {
    const bare = this.tryParseBareColumnRange();
    if (bare !== undefined) {
      if (typeof bare === "string") {
        return bare;
      }
      return bare.map((value) => [value]);
    }
    const startTok = this.peek();
    if (startTok.kind !== "cell") {
      return undefined;
    }
    const startIndex = this.pos;
    if (this.tokens[this.pos + 1]?.kind !== "colon") {
      return undefined;
    }
    const endIndex = startIndex + 2;
    const endTok = this.rangeEndToken(endIndex);
    if (!endTok) {
      return undefined;
    }
    const range = this.resolveTokenRange(startTok, endTok);
    if (typeof range === "string") {
      return range;
    }
    this.pos = endIndex + 1;
    return buildMatrixFromRangeRefs(range.startRef, range.endRef, (ref) => {
      const v = this.env.getCell(ref);
      return isSheetError(v) ? v : v;
    });
  }

  private tryParseRangeArg(): SheetEvalResult[] | string | undefined {
    const bare = this.tryParseBareColumnRange();
    if (bare !== undefined) {
      return bare;
    }
    const startTok = this.peek();
    if (startTok.kind !== "cell") {
      return undefined;
    }
    if (this.tokens[this.pos + 1]?.kind !== "colon") {
      return undefined;
    }
    const startIndex = this.pos;
    const endIndex = startIndex + 2;
    const endTok = this.rangeEndToken(endIndex);
    if (!endTok) {
      return undefined;
    }
    const range = this.resolveTokenRange(startTok, endTok);
    if (typeof range === "string") {
      return range;
    }
    this.pos = endIndex + 1;
    return this.readRangeValues(range.startRef, range.endRef);
  }

  private readRangeValues(startRef: string, endRef: string): SheetEvalResult[] | string {
    const start = parseQualifiedCellRef(startRef);
    const end = parseQualifiedCellRef(endRef);
    const sheetName = start.sheetName ?? end.sheetName ?? this.env.defaultSheet;
    const cells = expandRange(start.address, end.address);
    if (!cells) {
      return ERROR.ref;
    }
    const values: SheetEvalResult[] = [];
    for (const addr of cells) {
      const ref = sheetName ? `${sheetName}!${addr}` : addr;
      const v = this.env.getCell(ref);
      if (isSheetError(v)) {
        return v;
      }
      values.push(v);
    }
    return values;
  }

  private peek(): Token {
    return this.tokens[this.pos] ?? { kind: "eof" };
  }

  private match(kind: Token["kind"], value?: string): boolean {
    const tok = this.peek();
    if (tok.kind !== kind) {
      return false;
    }
    if (value !== undefined && (tok.kind === "op" || tok.kind === "ident") && tok.value !== value) {
      return false;
    }
    this.pos++;
    return true;
  }

  private check(kind: Token["kind"]): boolean {
    return this.peek().kind === kind;
  }
}

function coerceNumber(value: unknown): number {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === "boolean") {
    return value ? 1 : 0;
  }
  const num = Number.parseFloat(String(value ?? ""));
  return Number.isFinite(num) ? num : 0;
}

function coerceBool(value: SheetEvalResult): boolean {
  if (typeof value === "boolean") {
    return value;
  }
  if (typeof value === "number") {
    return value !== 0;
  }
  return String(value ?? "").length > 0 && String(value).toLowerCase() !== "false";
}

function numbersFromArgs(args: SheetEvalResult[]): number[] | string {
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

function invokeFunction(
  name: string,
  args: SheetEvalResult[],
  env: SheetEvalEnvironment
): SheetEvalResult {
  name = normalizeFunctionName(name);

  if (name === "IFERROR") {
    if (args.length < 2) {
      return ERROR.value;
    }
    const primary = args[0];
    if (isSheetError(primary)) {
      return args[1];
    }
    return primary;
  }

  if (name === "IFNA") {
    if (args.length < 2) {
      return ERROR.value;
    }
    return excelIfna(args[0], args[1]);
  }

  for (const arg of args) {
    if (isSheetError(arg)) {
      return arg;
    }
  }

  if (name === "ABS") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return Math.abs(coerceNumber(args[0]));
  }

  if (name === "AND") {
    return args.every((arg) => coerceBool(arg));
  }

  if (name === "OR") {
    return args.some((arg) => coerceBool(arg));
  }

  if (name === "NOT") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return !coerceBool(args[0]);
  }

  if (name === "PRODUCT") {
    const nums = numbersFromArgs(args);
    if (typeof nums === "string") {
      return nums;
    }
    return nums.reduce((a, b) => a * b, nums.length > 0 ? 1 : 0);
  }

  if (name === "IF") {
    if (args.length < 2) {
      return ERROR.value;
    }
    return coerceBool(args[0]) ? args[1] : args[2] ?? false;
  }

  if (name === "ROUND") {
    if (args.length < 1) {
      return ERROR.value;
    }
    const digits = args.length > 1 ? Math.trunc(coerceNumber(args[1])) : 0;
    const factor = 10 ** digits;
    return Math.round(coerceNumber(args[0]) * factor) / factor;
  }

  if (name === "ROUNDUP") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return excelRoundUp(
      coerceNumber(args[0]),
      args.length > 1 ? Math.trunc(coerceNumber(args[1])) : 0
    );
  }

  if (name === "ROUNDDOWN") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return excelRoundDown(
      coerceNumber(args[0]),
      args.length > 1 ? Math.trunc(coerceNumber(args[1])) : 0
    );
  }

  if (name === "MEDIAN") {
    const nums = numbersFromArgs(args);
    if (typeof nums === "string") {
      return nums;
    }
    return excelMedian(nums);
  }

  if (name === "STDEV.S" || name === "STDEV") {
    const nums = numbersFromArgs(args);
    if (typeof nums === "string") {
      return nums;
    }
    return excelStdevS(nums);
  }

  if (name === "MOD") {
    if (args.length < 2) {
      return ERROR.value;
    }
    const divisor = coerceNumber(args[1]);
    if (divisor === 0) {
      return ERROR.div0;
    }
    return coerceNumber(args[0]) % divisor;
  }

  if (name === "POWER") {
    if (args.length < 2) {
      return ERROR.value;
    }
    return coerceNumber(args[0]) ** coerceNumber(args[1]);
  }

  if (name === "SQRT") {
    if (args.length < 1) {
      return ERROR.value;
    }
    const n = coerceNumber(args[0]);
    if (n < 0) {
      return ERROR.value;
    }
    return Math.sqrt(n);
  }

  if (name === "INT") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return Math.trunc(coerceNumber(args[0]));
  }

  if (name === "CEILING") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return Math.ceil(coerceNumber(args[0]));
  }

  if (name === "FLOOR") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return Math.floor(coerceNumber(args[0]));
  }

  if (name === "LEN") {
    return String(args[0] ?? "").length;
  }

  if (name === "LEFT") {
    const text = String(args[0] ?? "");
    const n = args.length > 1 ? Math.max(0, Math.trunc(coerceNumber(args[1]))) : 1;
    return text.slice(0, n);
  }

  if (name === "RIGHT") {
    const text = String(args[0] ?? "");
    const n = args.length > 1 ? Math.max(0, Math.trunc(coerceNumber(args[1]))) : 1;
    return text.slice(Math.max(0, text.length - n));
  }

  if (name === "MID") {
    if (args.length < 3) {
      return ERROR.value;
    }
    const text = String(args[0] ?? "");
    const start = Math.max(1, Math.trunc(coerceNumber(args[1]))) - 1;
    const len = Math.max(0, Math.trunc(coerceNumber(args[2])));
    return text.slice(start, start + len);
  }

  if (name === "TRIM") {
    return String(args[0] ?? "").trim();
  }

  if (name === "UPPER") {
    return String(args[0] ?? "").toUpperCase();
  }

  if (name === "LOWER") {
    return String(args[0] ?? "").toLowerCase();
  }

  if (name === "CONCAT" || name === "CONCATENATE") {
    return args.map((arg) => String(arg ?? "")).join("");
  }

  if (name === "TEXT") {
    if (args.length < 2) {
      return ERROR.value;
    }
    const num = coerceNumber(args[0]);
    const fmt = String(args[1]);
    const decimals = (fmt.match(/0\.(0+)/)?.[1]?.length ?? 0);
    return num.toFixed(decimals);
  }

  if (name === "TODAY") {
    return excelTodaySerial();
  }

  if (name === "NOW") {
    return excelNowSerial();
  }

  if (name === "YEAR") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return excelYear(coerceNumber(args[0]));
  }

  if (name === "MONTH") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return excelMonth(coerceNumber(args[0]));
  }

  if (name === "DAY") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return excelDay(coerceNumber(args[0]));
  }

  if (name === "DATE") {
    if (args.length < 3) {
      return ERROR.value;
    }
    return excelDate(coerceNumber(args[0]), coerceNumber(args[1]), coerceNumber(args[2]));
  }

  if (name === "DAYS") {
    if (args.length < 2) {
      return ERROR.value;
    }
    return excelDays(coerceNumber(args[0]), coerceNumber(args[1]));
  }

  if (name === "WEEKDAY") {
    if (args.length < 1) {
      return ERROR.value;
    }
    const returnType = args.length > 1 ? Math.trunc(coerceNumber(args[1])) : 1;
    return excelWeekday(coerceNumber(args[0]), returnType);
  }

  if (name === "HOUR") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return excelHour(coerceNumber(args[0]));
  }

  if (name === "MINUTE") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return excelMinute(coerceNumber(args[0]));
  }

  if (name === "SECOND") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return excelSecond(coerceNumber(args[0]));
  }

  if (name === "TIME") {
    if (args.length < 3) {
      return ERROR.value;
    }
    return excelTime(coerceNumber(args[0]), coerceNumber(args[1]), coerceNumber(args[2]));
  }

  if (name === "EDATE") {
    if (args.length < 2) {
      return ERROR.value;
    }
    return excelEdate(coerceNumber(args[0]), coerceNumber(args[1]));
  }

  if (name === "EOMONTH") {
    if (args.length < 2) {
      return ERROR.value;
    }
    return excelEomonth(coerceNumber(args[0]), coerceNumber(args[1]));
  }

  if (name === "DATEDIF") {
    if (args.length < 3) {
      return ERROR.value;
    }
    return excelDatedif(
      coerceNumber(args[0]),
      coerceNumber(args[1]),
      String(args[2] ?? "D")
    );
  }

  if (name === "NETWORKDAYS") {
    if (args.length < 2) {
      return ERROR.value;
    }
    const holidays = args.slice(2).map((arg) => Math.trunc(coerceNumber(arg)));
    return excelNetworkdays(coerceNumber(args[0]), coerceNumber(args[1]), holidays);
  }

  if (name === "WORKDAY") {
    if (args.length < 2) {
      return ERROR.value;
    }
    const holidays = args.slice(2).map((arg) => Math.trunc(coerceNumber(arg)));
    return excelWorkday(coerceNumber(args[0]), coerceNumber(args[1]), holidays);
  }

  if (name === "RAND") {
    return excelRand();
  }

  if (name === "RANDBETWEEN") {
    if (args.length < 2) {
      return ERROR.value;
    }
    const value = excelRandBetween(coerceNumber(args[0]), coerceNumber(args[1]));
    if (!Number.isFinite(value)) {
      return ERROR.value;
    }
    return value;
  }

  if (name === "ISBLANK") {
    const v = args[0];
    return v === null || v === "" || v === undefined;
  }

  if (name === "ISNUMBER") {
    const v = args[0];
    return typeof v === "number" && Number.isFinite(v);
  }

  if (name === "ISTEXT") {
    const v = args[0];
    return typeof v === "string" && v !== "";
  }

  if (name === "ISERROR") {
    return isSheetError(args[0]);
  }

  if (name === "ISNA") {
    return args[0] === ERROR.na;
  }

  if (name === "ISERR") {
    return isSheetError(args[0]) && args[0] !== ERROR.na;
  }

  if (name === "PI") {
    return Math.PI;
  }

  if (name === "EXP") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return Math.exp(coerceNumber(args[0]));
  }

  if (name === "LN") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return excelLn(coerceNumber(args[0]));
  }

  if (name === "LOG10") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return excelLog10(coerceNumber(args[0]));
  }

  if (name === "LOG") {
    if (args.length < 1) {
      return ERROR.value;
    }
    const base = args.length > 1 ? coerceNumber(args[1]) : undefined;
    return excelLog(coerceNumber(args[0]), base);
  }

  if (name === "SIGN") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return excelSign(coerceNumber(args[0]));
  }

  if (name === "TRUNC") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return excelTrunc(
      coerceNumber(args[0]),
      args.length > 1 ? Math.trunc(coerceNumber(args[1])) : 0
    );
  }

  if (name === "MROUND") {
    if (args.length < 2) {
      return ERROR.value;
    }
    return excelMround(coerceNumber(args[0]), coerceNumber(args[1]));
  }

  if (name === "FIND") {
    if (args.length < 2) {
      return ERROR.value;
    }
    const start = args.length > 2 ? coerceNumber(args[2]) : 1;
    return excelFind(args[0], args[1], start);
  }

  if (name === "SEARCH") {
    if (args.length < 2) {
      return ERROR.value;
    }
    const start = args.length > 2 ? coerceNumber(args[2]) : 1;
    return excelSearch(args[0], args[1], start);
  }

  if (name === "SUBSTITUTE") {
    if (args.length < 3) {
      return ERROR.value;
    }
    const instance = args.length > 3 ? Math.trunc(coerceNumber(args[3])) : undefined;
    return excelSubstitute(args[0], args[1], args[2], instance);
  }

  if (name === "REPLACE") {
    if (args.length < 4) {
      return ERROR.value;
    }
    return excelReplace(args[0], coerceNumber(args[1]), coerceNumber(args[2]), args[3]);
  }

  if (name === "VALUE") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return excelValue(args[0]);
  }

  if (name === "EXACT") {
    if (args.length < 2) {
      return ERROR.value;
    }
    return excelExact(args[0], args[1]);
  }

  if (name === "DATEVALUE") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return excelDatevalue(args[0]);
  }

  if (name === "TIMEVALUE") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return excelTimevalue(args[0]);
  }

  if (name === "WEEKNUM") {
    if (args.length < 1) {
      return ERROR.value;
    }
    const returnType = args.length > 1 ? Math.trunc(coerceNumber(args[1])) : 1;
    return excelWeeknum(coerceNumber(args[0]), returnType);
  }

  if (name === "YEARFRAC") {
    if (args.length < 2) {
      return ERROR.value;
    }
    const basis = args.length > 2 ? Math.trunc(coerceNumber(args[2])) : 0;
    return excelYearfrac(coerceNumber(args[0]), coerceNumber(args[1]), basis);
  }

  if (name === "SWITCH") {
    if (args.length < 3) {
      return ERROR.value;
    }
    return excelSwitch(args[0], args.slice(1));
  }

  if (name === "CHOOSE") {
    if (args.length < 2) {
      return ERROR.value;
    }
    return excelChoose(Math.trunc(coerceNumber(args[0])), args.slice(1));
  }

  if (name === "VAR.S" || name === "VARS") {
    const nums = numbersFromFlat(args);
    return excelVarS(nums);
  }

  if (name === "VAR" || name === "VAR.P" || name === "VARP") {
    const nums = numbersFromFlat(args);
    return excelVarP(nums);
  }

  if (name === "PERCENTILE") {
    const nums = numbersFromFlat(args.slice(0, -1));
    if (args.length < 2) {
      return ERROR.value;
    }
    return excelPercentile(nums, coerceNumber(args[args.length - 1]));
  }

  if (name === "QUARTILE") {
    const nums = numbersFromFlat(args.slice(0, -1));
    if (args.length < 2) {
      return ERROR.value;
    }
    return excelQuartile(nums, coerceNumber(args[args.length - 1]));
  }

  if (name === "LARGE") {
    const nums = numbersFromFlat(args.slice(0, -1));
    if (args.length < 2) {
      return ERROR.value;
    }
    return excelLarge(nums, coerceNumber(args[args.length - 1]));
  }

  if (name === "SMALL") {
    const nums = numbersFromFlat(args.slice(0, -1));
    if (args.length < 2) {
      return ERROR.value;
    }
    return excelSmall(nums, coerceNumber(args[args.length - 1]));
  }

  if (name === "RANK") {
    if (args.length < 2) {
      return ERROR.value;
    }
    const number = coerceNumber(args[0]);
    if (args.length === 2) {
      return excelRank(number, numbersFromFlat(args.slice(1)), 0);
    }
    const order = Math.trunc(coerceNumber(args[args.length - 1]));
    return excelRank(number, numbersFromFlat(args.slice(1, -1)), order);
  }

  if (name === "NPV") {
    if (args.length < 2) {
      return ERROR.value;
    }
    const rate = coerceNumber(args[0]);
    const values = numbersFromFlat(args.slice(1));
    return excelNpv(rate, values);
  }

  if (name === "PMT") {
    if (args.length < 3) {
      return ERROR.value;
    }
    return excelPmt(
      coerceNumber(args[0]),
      coerceNumber(args[1]),
      coerceNumber(args[2]),
      args.length > 3 ? coerceNumber(args[3]) : 0,
      args.length > 4 ? Math.trunc(coerceNumber(args[4])) : 0
    );
  }

  if (name === "FV") {
    if (args.length < 3) {
      return ERROR.value;
    }
    return excelFv(
      coerceNumber(args[0]),
      coerceNumber(args[1]),
      coerceNumber(args[2]),
      args.length > 3 ? coerceNumber(args[3]) : 0,
      args.length > 4 ? Math.trunc(coerceNumber(args[4])) : 0
    );
  }

  if (name === "PV") {
    if (args.length < 3) {
      return ERROR.value;
    }
    return excelPv(
      coerceNumber(args[0]),
      coerceNumber(args[1]),
      coerceNumber(args[2]),
      args.length > 3 ? coerceNumber(args[3]) : 0,
      args.length > 4 ? Math.trunc(coerceNumber(args[4])) : 0
    );
  }

  if (name === "NPER") {
    if (args.length < 3) {
      return ERROR.value;
    }
    return excelNper(
      coerceNumber(args[0]),
      coerceNumber(args[1]),
      coerceNumber(args[2]),
      args.length > 3 ? coerceNumber(args[3]) : 0,
      args.length > 4 ? Math.trunc(coerceNumber(args[4])) : 0
    );
  }

  if (name === "RATE") {
    if (args.length < 3) {
      return ERROR.value;
    }
    return excelRate(
      coerceNumber(args[0]),
      coerceNumber(args[1]),
      coerceNumber(args[2]),
      args.length > 3 ? coerceNumber(args[3]) : 0,
      args.length > 4 ? Math.trunc(coerceNumber(args[4])) : 0,
      args.length > 5 ? coerceNumber(args[5]) : 0.1
    );
  }

  if (name === "IRR") {
    if (args.length < 1) {
      return ERROR.value;
    }
    if (args.length >= 2) {
      return excelIrr(numbersFromFlat(args.slice(0, -1)), coerceNumber(args[args.length - 1]));
    }
    return excelIrr(numbersFromFlat(args));
  }

  if (name === "SIN") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return Math.sin(coerceNumber(args[0]));
  }

  if (name === "COS") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return Math.cos(coerceNumber(args[0]));
  }

  if (name === "TAN") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return Math.tan(coerceNumber(args[0]));
  }

  if (name === "ASIN") {
    if (args.length < 1) {
      return ERROR.value;
    }
    const n = coerceNumber(args[0]);
    if (n < -1 || n > 1) {
      return ERROR.num;
    }
    return Math.asin(n);
  }

  if (name === "ACOS") {
    if (args.length < 1) {
      return ERROR.value;
    }
    const n = coerceNumber(args[0]);
    if (n < -1 || n > 1) {
      return ERROR.num;
    }
    return Math.acos(n);
  }

  if (name === "ATAN") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return Math.atan(coerceNumber(args[0]));
  }

  if (name === "ATAN2") {
    if (args.length < 2) {
      return ERROR.value;
    }
    return Math.atan2(coerceNumber(args[0]), coerceNumber(args[1]));
  }

  if (name === "RADIANS") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return (coerceNumber(args[0]) * Math.PI) / 180;
  }

  if (name === "DEGREES") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return (coerceNumber(args[0]) * 180) / Math.PI;
  }

  if (name === "COUNT") {
    return countNumeric(args);
  }

  if (name === "COUNTA") {
    return countNonBlank(args);
  }

  if (name === "COUNTBLANK") {
    return countBlank(args);
  }

  if (name === "ISPREF") {
    if (args.length < 2) {
      return ERROR.value;
    }
    const path = String(args[0]);
    const varName = String(args[1]);
    const field = args[2] !== undefined ? String(args[2]) : "value";
    const v = env.ispf.bindingValues.get(bindingCacheKey(path, varName, field));
    return coerceNumber(v);
  }

  if (name === "ISPSUM") {
    if (args.length < 2) {
      return ERROR.value;
    }
    const tableVar = String(args[0]);
    const column = String(args[1]);
    return env.ispf.tableColumnSums.get(`${tableVar}|${column}`) ?? 0;
  }

  if (name === "ISPHIST") {
    if (args.length < 2) {
      return ERROR.value;
    }
    const path = String(args[0]);
    const varName = String(args[1]);
    const minutes = args[2] !== undefined ? Math.trunc(coerceNumber(args[2])) : 5;
    const key = histCacheKey(path, varName, minutes);
    const hist = env.ispf.histValues.get(key);
    if (hist !== undefined) {
      return hist;
    }
    const fallback = env.ispf.bindingValues.get(bindingCacheKey(path, varName, "value"));
    return coerceNumber(fallback);
  }

  const aggregateFns = new Set(["SUM", "AVERAGE", "AVG", "MIN", "MAX"]);
  if (aggregateFns.has(name)) {
    const nums = numbersFromArgs(args);
    if (typeof nums === "string") {
      return nums;
    }
    if (nums.length === 0) {
      return 0;
    }
    switch (name) {
      case "SUM":
        return nums.reduce((a, b) => a + b, 0);
      case "AVERAGE":
      case "AVG":
        return nums.reduce((a, b) => a + b, 0) / nums.length;
      case "MIN":
        return Math.min(...nums);
      case "MAX":
        return Math.max(...nums);
      default:
        return ERROR.name;
    }
  }

  return ERROR.name;
}

/** Parse and evaluate a formula string (with or without leading `=`). */
export function evaluateSheetFormula(formula: string, env: SheetEvalEnvironment): SheetEvalResult {
  const stripped = formula.trim().startsWith("=") ? formula.trim().slice(1) : formula.trim();
  const normalized = normalizeFormulaSyntax(stripped);
  if (!normalized) {
    return "";
  }
  const tokens = tokenize(normalized);
  if (typeof tokens === "string") {
    return tokens;
  }
  return new Parser(tokens, env).parse();
}

export function literalCellValue(raw: string): SheetEvalResult {
  const trimmed = raw.trim();
  if (trimmed === "") {
    return null;
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
