import type { SheetEvalResult } from "./ispfSheetEval";
import { matchCriteria } from "./sheetExcelFunctions";

function asNumber(value: SheetEvalResult): number | null {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === "boolean") {
    return value ? 1 : 0;
  }
  const num = Number.parseFloat(String(value ?? ""));
  return Number.isFinite(num) ? num : null;
}

function asString(value: SheetEvalResult): string {
  if (value === null || value === undefined) {
    return "";
  }
  return String(value);
}

function valuesEqual(a: SheetEvalResult, b: SheetEvalResult): boolean {
  if (typeof a === "number" && typeof b === "number") {
    return a === b;
  }
  if (typeof a === "boolean" || typeof b === "boolean") {
    return Boolean(a) === Boolean(b);
  }
  return asString(a).toLowerCase() === asString(b).toLowerCase();
}

function rowMatchesAllCriteria(
  index: number,
  criteriaRanges: SheetEvalResult[][],
  criteria: SheetEvalResult[]
): boolean {
  for (let c = 0; c < criteria.length; c++) {
    const range = criteriaRanges[c];
    if (!range || index >= range.length) {
      return false;
    }
    if (!matchCriteria(criteria[c], range[index])) {
      return false;
    }
  }
  return true;
}

function lengthsMatch(ranges: SheetEvalResult[][]): boolean {
  if (ranges.length === 0) {
    return false;
  }
  const len = ranges[0].length;
  return ranges.every((range) => range.length === len);
}

export function excelXlookup(
  lookup: SheetEvalResult,
  lookupArray: SheetEvalResult[],
  returnArray: SheetEvalResult[],
  ifNotFound: SheetEvalResult = "#N/A",
  matchMode = 0,
  searchMode = 1
): SheetEvalResult {
  if (lookupArray.length !== returnArray.length) {
    return "#REF!";
  }
  const indices =
    searchMode >= 0
      ? Array.from({ length: lookupArray.length }, (_, i) => i)
      : Array.from({ length: lookupArray.length }, (_, i) => lookupArray.length - 1 - i);

  if (matchMode === 0 || matchMode === 2) {
    for (const i of indices) {
      const matched =
        matchMode === 2
          ? matchCriteria(asString(lookup), lookupArray[i])
          : valuesEqual(lookupArray[i], lookup);
      if (matched) {
        return returnArray[i] ?? ifNotFound;
      }
    }
    return ifNotFound;
  }

  const lookupNum = asNumber(lookup);
  if (lookupNum === null) {
    return ifNotFound;
  }

  if (matchMode < 0) {
    let best = -1;
    let bestVal = -Infinity;
    for (const i of indices) {
      const n = asNumber(lookupArray[i]);
      if (n === null || n > lookupNum) {
        continue;
      }
      if (n >= bestVal) {
        bestVal = n;
        best = i;
      }
    }
    return best >= 0 ? (returnArray[best] ?? ifNotFound) : ifNotFound;
  }

  let best = -1;
  let bestVal = Infinity;
  for (const i of indices) {
    const n = asNumber(lookupArray[i]);
    if (n === null || n < lookupNum) {
      continue;
    }
    if (n <= bestVal) {
      bestVal = n;
      best = i;
    }
  }
  return best >= 0 ? (returnArray[best] ?? ifNotFound) : ifNotFound;
}

export function excelSumifs(
  sumRange: SheetEvalResult[],
  criteriaRanges: SheetEvalResult[][],
  criteria: SheetEvalResult[]
): SheetEvalResult {
  if (!lengthsMatch([sumRange, ...criteriaRanges])) {
    return "#REF!";
  }
  let total = 0;
  for (let i = 0; i < sumRange.length; i++) {
    if (!rowMatchesAllCriteria(i, criteriaRanges, criteria)) {
      continue;
    }
    const n = asNumber(sumRange[i]);
    if (n !== null) {
      total += n;
    }
  }
  return total;
}

export function excelCountifs(
  criteriaRanges: SheetEvalResult[][],
  criteria: SheetEvalResult[]
): SheetEvalResult {
  if (!lengthsMatch(criteriaRanges)) {
    return "#REF!";
  }
  const len = criteriaRanges[0]?.length ?? 0;
  let count = 0;
  for (let i = 0; i < len; i++) {
    if (rowMatchesAllCriteria(i, criteriaRanges, criteria)) {
      count++;
    }
  }
  return count;
}

export function excelAverageifs(
  avgRange: SheetEvalResult[],
  criteriaRanges: SheetEvalResult[][],
  criteria: SheetEvalResult[]
): SheetEvalResult {
  if (!lengthsMatch([avgRange, ...criteriaRanges])) {
    return "#REF!";
  }
  let total = 0;
  let count = 0;
  for (let i = 0; i < avgRange.length; i++) {
    if (!rowMatchesAllCriteria(i, criteriaRanges, criteria)) {
      continue;
    }
    const n = asNumber(avgRange[i]);
    if (n !== null) {
      total += n;
      count++;
    }
  }
  return count > 0 ? total / count : "#DIV/0!";
}

export function excelSumproduct(arrays: SheetEvalResult[][]): SheetEvalResult {
  if (arrays.length === 0 || !lengthsMatch(arrays)) {
    return "#REF!";
  }
  const len = arrays[0].length;
  let total = 0;
  for (let i = 0; i < len; i++) {
    let product = 1;
    for (const array of arrays) {
      const n = asNumber(array[i]);
      if (n === null) {
        product = 0;
        break;
      }
      product *= n;
    }
    total += product;
  }
  return total;
}

function subtotalAggregate(fnNum: number, values: number[]): SheetEvalResult {
  const mapped = fnNum >= 100 ? fnNum - 100 : fnNum;
  if (values.length === 0) {
    return mapped === 2 || mapped === 3 ? 0 : "#DIV/0!";
  }
  switch (mapped) {
    case 1:
      return values.reduce((a, b) => a + b, 0) / values.length;
    case 2:
      return values.length;
    case 3:
      return values.length;
    case 4:
      return Math.max(...values);
    case 5:
      return Math.min(...values);
    case 6:
      return values.reduce((a, b) => a * b, 1);
    case 7:
      return excelStdevS(values);
    case 8: {
      const mean = values.reduce((a, b) => a + b, 0) / values.length;
      const variance = values.reduce((a, v) => a + (v - mean) ** 2, 0) / values.length;
      return Math.sqrt(variance);
    }
    case 9:
      return values.reduce((a, b) => a + b, 0);
    case 10: {
      const mean = values.reduce((a, b) => a + b, 0) / values.length;
      return values.reduce((a, v) => a + (v - mean) ** 2, 0) / (values.length - 1);
    }
    case 11: {
      const mean = values.reduce((a, b) => a + b, 0) / values.length;
      return values.reduce((a, v) => a + (v - mean) ** 2, 0) / values.length;
    }
    default:
      return "#VALUE!";
  }
}

export function excelSubtotal(functionNum: number, args: SheetEvalResult[]): SheetEvalResult {
  const nums = args.map((v) => asNumber(v)).filter((n): n is number => n !== null);
  return subtotalAggregate(Math.trunc(functionNum), nums);
}

function coerceIfsBool(value: SheetEvalResult): boolean {
  if (typeof value === "boolean") {
    return value;
  }
  if (typeof value === "number") {
    return value !== 0;
  }
  const text = asString(value);
  return text.length > 0 && text.toLowerCase() !== "false";
}

export function excelIfs(pairs: SheetEvalResult[]): SheetEvalResult {
  for (let i = 0; i + 1 < pairs.length; i += 2) {
    if (coerceIfsBool(pairs[i])) {
      return pairs[i + 1];
    }
  }
  return "#N/A";
}

export function excelTextjoin(
  delimiter: SheetEvalResult,
  ignoreEmpty: boolean,
  parts: SheetEvalResult[]
): SheetEvalResult {
  const sep = asString(delimiter);
  const chunks = parts
    .map((part) => asString(part))
    .filter((part) => !ignoreEmpty || part.length > 0);
  return chunks.join(sep);
}

export function excelMedian(values: number[]): SheetEvalResult {
  if (values.length === 0) {
    return "#NUM!";
  }
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  if (sorted.length % 2 === 0) {
    return (sorted[mid - 1] + sorted[mid]) / 2;
  }
  return sorted[mid];
}

export function excelStdevS(values: number[]): SheetEvalResult {
  if (values.length < 2) {
    return "#DIV/0!";
  }
  const mean = values.reduce((a, b) => a + b, 0) / values.length;
  const variance = values.reduce((a, v) => a + (v - mean) ** 2, 0) / (values.length - 1);
  return Math.sqrt(variance);
}

export function excelRoundUp(value: number, digits = 0): number {
  const factor = 10 ** digits;
  if (value >= 0) {
    return Math.ceil(value * factor) / factor;
  }
  return Math.floor(value * factor) / factor;
}

export function excelRoundDown(value: number, digits = 0): number {
  const factor = 10 ** digits;
  if (value >= 0) {
    return Math.floor(value * factor) / factor;
  }
  return Math.ceil(value * factor) / factor;
}
