// Sheet formula evaluator — public entry points. Implementation lives in sheetEval*.ts:
// core (types / errors / coercions), tokenizer, parser, functions (dispatcher + per-category registries).

export {
  expandRange,
  isSheetError,
  type SheetEvalEnvironment,
  type SheetEvalResult,
  type SheetEvalValue,
} from "./sheetEvalCore";

import type { SheetEvalEnvironment, SheetEvalResult } from "./sheetEvalCore";
import { Parser } from "./sheetEvalParser";
import { tokenize } from "./sheetEvalTokenizer";
import { normalizeFormulaSyntax } from "./sheetFormulaNormalize";

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
