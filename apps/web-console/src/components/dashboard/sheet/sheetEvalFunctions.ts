// Scalar function dispatcher: error-tolerant functions first, then error propagation, then the
// category registries, then the SUM/AVERAGE/MIN/MAX fallback for flat argument lists.
import { LOGICAL_ERROR_TOLERANT_FUNCTIONS, LOGICAL_FUNCTIONS } from "./sheetEvalFunctionsLogical";
import { MATH_FUNCTIONS } from "./sheetEvalFunctionsMath";
import { TEXT_FUNCTIONS } from "./sheetEvalFunctionsText";
import { DATE_FUNCTIONS } from "./sheetEvalFunctionsDate";
import { FINANCIAL_FUNCTIONS } from "./sheetEvalFunctionsFinancial";
import { ISPF_FUNCTIONS } from "./sheetEvalFunctionsIspf";
import {
  ERROR,
  isSheetError,
  numbersFromArgs,
  type SheetEvalEnvironment,
  type SheetEvalResult,
  type SheetFunction,
  type SheetFunctionRegistry,
} from "./sheetEvalCore";
import { normalizeFunctionName } from "./sheetFormulaNormalize";

const ERROR_TOLERANT_FUNCTIONS: SheetFunctionRegistry = {
  ...LOGICAL_ERROR_TOLERANT_FUNCTIONS,
};

const FUNCTIONS: SheetFunctionRegistry = {
  ...LOGICAL_FUNCTIONS,
  ...MATH_FUNCTIONS,
  ...TEXT_FUNCTIONS,
  ...DATE_FUNCTIONS,
  ...FINANCIAL_FUNCTIONS,
  ...ISPF_FUNCTIONS,
};

const aggregateFns = new Set(["SUM", "AVERAGE", "AVG", "MIN", "MAX"]);

function lookup(registry: SheetFunctionRegistry, name: string): SheetFunction | undefined {
  return Object.hasOwn(registry, name) ? registry[name] : undefined;
}

export function invokeFunction(
  name: string,
  args: SheetEvalResult[],
  env: SheetEvalEnvironment
): SheetEvalResult {
  name = normalizeFunctionName(name);

  const tolerant = lookup(ERROR_TOLERANT_FUNCTIONS, name);
  if (tolerant) {
    return tolerant(args, env);
  }

  for (const arg of args) {
    if (isSheetError(arg)) {
      return arg;
    }
  }

  const fn = lookup(FUNCTIONS, name);
  if (fn) {
    return fn(args, env);
  }

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
