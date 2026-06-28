import type { SheetEvalResult } from "./ispfSheetEval";
import { excelDate, dateToExcelSerial } from "./sheetDateFunctions";

const NUM_ERROR = "#NUM!" as const;

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

function numbersFromFlat(args: SheetEvalResult[]): number[] {
  const nums: number[] = [];
  for (const arg of args) {
    const n = asNumber(arg);
    if (n !== null) {
      nums.push(n);
    }
  }
  return nums;
}

export function excelLog(value: number, base?: number): number | string {
  if (value <= 0) {
    return NUM_ERROR;
  }
  const b = base ?? 10;
  if (b <= 0 || b === 1) {
    return NUM_ERROR;
  }
  return Math.log(value) / Math.log(b);
}

export function excelLn(value: number): number | string {
  if (value <= 0) {
    return NUM_ERROR;
  }
  return Math.log(value);
}

export function excelLog10(value: number): number | string {
  if (value <= 0) {
    return NUM_ERROR;
  }
  return Math.log10(value);
}

export function excelSign(value: number): number {
  if (value > 0) {
    return 1;
  }
  if (value < 0) {
    return -1;
  }
  return 0;
}

export function excelTrunc(value: number, digits = 0): number {
  const factor = 10 ** Math.trunc(digits);
  return Math.trunc(value * factor) / factor;
}

export function excelMround(value: number, multiple: number): number | string {
  if (multiple === 0) {
    return 0;
  }
  if (Math.sign(value) !== Math.sign(multiple)) {
    return NUM_ERROR;
  }
  return Math.round(value / multiple) * multiple;
}

export function excelFind(
  findText: SheetEvalResult,
  withinText: SheetEvalResult,
  startNum = 1
): number | string {
  const needle = String(findText ?? "");
  const haystack = String(withinText ?? "");
  const start = Math.max(1, Math.trunc(asNumber(startNum as SheetEvalResult) ?? 1));
  const idx = haystack.indexOf(needle, start - 1);
  return idx < 0 ? "#VALUE!" : idx + 1;
}

function wildcardToRegex(pattern: string): RegExp {
  let escaped = "";
  for (const ch of pattern) {
    if (ch === "?") {
      escaped += ".";
    } else if (ch === "*") {
      escaped += ".*";
    } else if (ch === "~") {
      continue;
    } else {
      escaped += ch.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    }
  }
  return new RegExp(`^${escaped}$`, "i");
}

export function excelSearch(
  findText: SheetEvalResult,
  withinText: SheetEvalResult,
  startNum = 1
): number | string {
  const needle = String(findText ?? "");
  const haystack = String(withinText ?? "");
  const start = Math.max(1, Math.trunc(asNumber(startNum as SheetEvalResult) ?? 1));
  if (needle.includes("?") || needle.includes("*")) {
    const slice = haystack.slice(start - 1);
    const re = wildcardToRegex(needle);
    const match = re.exec(slice);
    return match ? start + match.index : "#VALUE!";
  }
  const idx = haystack.toLowerCase().indexOf(needle.toLowerCase(), start - 1);
  return idx < 0 ? "#VALUE!" : idx + 1;
}

export function excelSubstitute(
  text: SheetEvalResult,
  oldText: SheetEvalResult,
  newText: SheetEvalResult,
  instanceNum?: number
): string {
  const source = String(text ?? "");
  const oldStr = String(oldText ?? "");
  const newStr = String(newText ?? "");
  if (instanceNum === undefined) {
    return source.split(oldStr).join(newStr);
  }
  const nth = Math.trunc(instanceNum);
  if (nth < 1) {
    return source;
  }
  let pos = -1;
  let from = 0;
  for (let i = 0; i < nth; i++) {
    pos = source.indexOf(oldStr, from);
    if (pos < 0) {
      return source;
    }
    from = pos + oldStr.length;
  }
  return source.slice(0, pos) + newStr + source.slice(pos + oldStr.length);
}

export function excelReplace(
  oldText: SheetEvalResult,
  startNum: number,
  numChars: number,
  newText: SheetEvalResult
): string {
  const source = String(oldText ?? "");
  const start = Math.max(1, Math.trunc(startNum)) - 1;
  const len = Math.max(0, Math.trunc(numChars));
  return source.slice(0, start) + String(newText ?? "") + source.slice(start + len);
}

export function excelValue(text: SheetEvalResult): number | string {
  const raw = String(text ?? "").trim();
  if (!raw) {
    return 0;
  }
  const normalized = raw.replace(/\s/g, "").replace(",", ".");
  const num = Number.parseFloat(normalized);
  return Number.isFinite(num) ? num : "#VALUE!";
}

export function excelExact(a: SheetEvalResult, b: SheetEvalResult): boolean {
  return String(a ?? "") === String(b ?? "");
}

export function excelIfna(value: SheetEvalResult, fallback: SheetEvalResult): SheetEvalResult {
  return value === "#N/A" ? fallback : value;
}

export function excelSwitch(expression: SheetEvalResult, pairs: SheetEvalResult[]): SheetEvalResult {
  if (pairs.length < 2) {
    return "#VALUE!";
  }
  const hasDefault = pairs.length % 2 === 1;
  const limit = hasDefault ? pairs.length - 1 : pairs.length;
  for (let i = 0; i < limit; i += 2) {
    if (pairs[i] === expression) {
      return pairs[i + 1];
    }
  }
  return hasDefault ? pairs[pairs.length - 1] : "#N/A";
}

export function excelChoose(index: number, values: SheetEvalResult[]): SheetEvalResult {
  const i = Math.trunc(index);
  if (i < 1 || i > values.length) {
    return "#VALUE!";
  }
  return values[i - 1];
}

export function excelVarS(nums: number[]): number | string {
  if (nums.length < 2) {
    return "#DIV/0!";
  }
  const mean = nums.reduce((a, b) => a + b, 0) / nums.length;
  const sumSq = nums.reduce((acc, n) => acc + (n - mean) ** 2, 0);
  return sumSq / (nums.length - 1);
}

export function excelVarP(nums: number[]): number | string {
  if (nums.length < 1) {
    return "#DIV/0!";
  }
  const mean = nums.reduce((a, b) => a + b, 0) / nums.length;
  const sumSq = nums.reduce((acc, n) => acc + (n - mean) ** 2, 0);
  return sumSq / nums.length;
}

function sortedCopy(nums: number[]): number[] {
  return [...nums].sort((a, b) => a - b);
}

export function excelPercentile(nums: number[], k: number): number | string {
  if (nums.length === 0 || k < 0 || k > 1) {
    return "#NUM!";
  }
  const sorted = sortedCopy(nums);
  const index = k * (sorted.length - 1);
  const lower = Math.floor(index);
  const upper = Math.ceil(index);
  if (lower === upper) {
    return sorted[lower];
  }
  const weight = index - lower;
  return sorted[lower] * (1 - weight) + sorted[upper] * weight;
}

export function excelQuartile(nums: number[], quart: number): number | string {
  const q = Math.trunc(quart);
  const map: Record<number, number> = { 0: 0, 1: 0.25, 2: 0.5, 3: 0.75, 4: 1 };
  if (!(q in map)) {
    return "#NUM!";
  }
  return excelPercentile(nums, map[q]);
}

export function excelLarge(nums: number[], k: number): number | string {
  const n = Math.trunc(k);
  if (n < 1 || n > nums.length) {
    return "#NUM!";
  }
  return sortedCopy(nums)[nums.length - n];
}

export function excelSmall(nums: number[], k: number): number | string {
  const n = Math.trunc(k);
  if (n < 1 || n > nums.length) {
    return "#NUM!";
  }
  return sortedCopy(nums)[n - 1];
}

export function excelRank(number: number, ref: number[], order = 0): number | string {
  if (ref.length === 0) {
    return "#N/A";
  }
  const ascending = order !== 0;
  const sorted = sortedCopy(ref);
  if (ascending) {
    const idx = sorted.indexOf(number);
    return idx < 0 ? "#N/A" : idx + 1;
  }
  const desc = [...sorted].reverse();
  const idx = desc.indexOf(number);
  return idx < 0 ? "#N/A" : idx + 1;
}

export function excelNpv(rate: number, values: number[]): number {
  let total = 0;
  for (let i = 0; i < values.length; i++) {
    total += values[i] / (1 + rate) ** (i + 1);
  }
  return total;
}

export function excelPmt(
  rate: number,
  nper: number,
  pv: number,
  fv = 0,
  type = 0
): number | string {
  if (nper === 0) {
    return "#NUM!";
  }
  if (rate === 0) {
    return -(pv + fv) / nper;
  }
  const pow = (1 + rate) ** nper;
  const pmt = (rate * (pv * pow + fv)) / ((1 + rate * type) * (pow - 1));
  return -pmt;
}

export function excelFv(
  rate: number,
  nper: number,
  pmt: number,
  pv = 0,
  type = 0
): number {
  if (rate === 0) {
    return -(pv + pmt * nper);
  }
  const pow = (1 + rate) ** nper;
  return -pv * pow - (pmt * (1 + rate * type) * (pow - 1)) / rate;
}

export function excelPv(
  rate: number,
  nper: number,
  pmt: number,
  fv = 0,
  type = 0
): number | string {
  if (rate === 0) {
    return -(fv + pmt * nper);
  }
  const pow = (1 + rate) ** nper;
  return -(fv + (pmt * (1 + rate * type) * (pow - 1)) / rate) / pow;
}

export function excelNper(
  rate: number,
  pmt: number,
  pv: number,
  fv = 0,
  type = 0
): number | string {
  if (rate === 0) {
    if (pmt === 0) {
      return NUM_ERROR;
    }
    return -(pv + fv) / pmt;
  }
  const adjustedPv = pv * (1 + rate * type);
  const num = pmt - fv * rate;
  const den = pmt + adjustedPv * rate;
  if (num / den <= 0) {
    return NUM_ERROR;
  }
  return Math.log(num / den) / Math.log(1 + rate);
}

export function excelRate(
  nper: number,
  pmt: number,
  pv: number,
  fv = 0,
  type = 0,
  guess = 0.1
): number | string {
  let rate = guess;
  for (let iter = 0; iter < 100; iter++) {
    const f = excelFv(rate, nper, pmt, pv, type) + fv;
    const delta = 1e-7;
    const fPrime = (excelFv(rate + delta, nper, pmt, pv, type) + fv - f) / delta;
    if (Math.abs(fPrime) < 1e-12) {
      break;
    }
    const next = rate - f / fPrime;
    if (Math.abs(next - rate) < 1e-10) {
      return next;
    }
    rate = next;
  }
  return NUM_ERROR;
}

export function excelIrr(values: number[], guess = 0.1): number | string {
  if (values.length < 2) {
    return "#NUM!";
  }
  let rate = guess;
  for (let iter = 0; iter < 100; iter++) {
    let npv = 0;
    let dnpv = 0;
    for (let t = 0; t < values.length; t++) {
      const factor = (1 + rate) ** t;
      npv += values[t] / factor;
      if (t > 0) {
        dnpv -= (t * values[t]) / ((1 + rate) ** (t + 1));
      }
    }
    if (Math.abs(npv) < 1e-10) {
      return rate;
    }
    if (Math.abs(dnpv) < 1e-12) {
      break;
    }
    rate -= npv / dnpv;
  }
  return NUM_ERROR;
}

export function excelDatevalue(text: SheetEvalResult): number | string {
  if (typeof text === "number" && Number.isFinite(text)) {
    return Math.trunc(text);
  }
  const raw = String(text ?? "").trim();
  if (!raw) {
    return "#VALUE!";
  }
  const iso = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(raw);
  if (iso) {
    return excelDate(Number(iso[1]), Number(iso[2]), Number(iso[3]));
  }
  const slash = /^(\d{1,2})[/.-](\d{1,2})[/.-](\d{2,4})$/.exec(raw);
  if (slash) {
    let year = Number(slash[3]);
    if (year < 100) {
      year += year < 30 ? 2000 : 1900;
    }
    return excelDate(year, Number(slash[1]), Number(slash[2]));
  }
  const parsed = Date.parse(raw);
  if (Number.isFinite(parsed)) {
    return dateToExcelSerial(new Date(parsed));
  }
  return "#VALUE!";
}

export function excelTimevalue(text: SheetEvalResult): number | string {
  if (typeof text === "number" && Number.isFinite(text)) {
    return text - Math.trunc(text);
  }
  const raw = String(text ?? "").trim();
  if (!raw) {
    return 0;
  }
  const match = /^(\d{1,2}):(\d{1,2})(?::(\d{1,2}))?\s*(AM|PM)?$/i.exec(raw);
  if (match) {
    let hour = Number(match[1]);
    const minute = Number(match[2]);
    const second = Number(match[3] ?? 0);
    const ampm = match[4]?.toUpperCase();
    if (ampm === "PM" && hour < 12) {
      hour += 12;
    }
    if (ampm === "AM" && hour === 12) {
      hour = 0;
    }
    return (hour * 3600 + minute * 60 + second) / 86_400;
  }
  const num = Number.parseFloat(raw);
  return Number.isFinite(num) ? num : "#VALUE!";
}

export function excelWeeknum(serial: number, returnType = 1): number {
  const date = new Date(Date.UTC(1899, 11, 30) + Math.trunc(serial) * 86_400_000);
  if (returnType === 21) {
    const d = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
    d.setUTCDate(d.getUTCDate() + 4 - (d.getUTCDay() || 7));
    const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1));
    return Math.ceil(((d.getTime() - yearStart.getTime()) / 86_400_000 + 1) / 7);
  }
  const start = new Date(Date.UTC(date.getUTCFullYear(), 0, 1));
  const dayOfYear = Math.floor((date.getTime() - start.getTime()) / 86_400_000) + 1;
  const startDow = start.getUTCDay() || 7;
  return Math.ceil((dayOfYear + startDow - 1) / 7);
}

export function excelYearfrac(startSerial: number, endSerial: number, basis = 0): number | string {
  const start = Math.trunc(startSerial);
  const end = Math.trunc(endSerial);
  if (basis === 0) {
    return (end - start) / 360;
  }
  if (basis === 1) {
    return (end - start) / 365.25;
  }
  return (end - start) / 360;
}

export { numbersFromFlat };
