// Recursive-descent parser + evaluator for sheet formulas. Range-taking functions are handled
// here (they need unevaluated refs); scalar functions are dispatched through invokeFunction.
import { a1ToRowCol, DEFAULT_SHEET_BOUNDS, resolveRangeEndpoints, type SheetBounds } from "./sheetAddress";
import {
  coerceBool,
  coerceNumber,
  ERROR,
  expandRange,
  isSheetError,
  type SheetEvalEnvironment,
  type SheetEvalResult,
} from "./sheetEvalCore";
import { invokeFunction } from "./sheetEvalFunctions";
import type { Token } from "./sheetEvalTokenizer";
import {
  buildMatrixFromRangeRefs,
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
  excelAverageifs,
  excelCountifs,
  excelIfs,
  excelMaxifs,
  excelMinifs,
  excelSubtotal,
  excelSumifs,
  excelSumproduct,
  excelTextjoin,
  excelXlookup,
} from "./sheetExcelFunctionsReport";
import { normalizeFunctionName } from "./sheetFormulaNormalize";
import { parseQualifiedCellRef } from "./sheetWorkbook";

export class Parser {
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
      case "MAXIFS":
        result = this.parseMaxifsArgs();
        break;
      case "MINIFS":
        result = this.parseMinifsArgs();
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

  private parseMaxifsArgs(): SheetEvalResult {
    const maxRange = this.requireRangeValues();
    if (typeof maxRange === "string") {
      return maxRange;
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
    return excelMaxifs(maxRange, criteriaRanges, criteria);
  }

  private parseMinifsArgs(): SheetEvalResult {
    const minRange = this.requireRangeValues();
    if (typeof minRange === "string") {
      return minRange;
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
    return excelMinifs(minRange, criteriaRanges, criteria);
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
