// Math functions of the sheet formula evaluator (args already evaluated).
import { excelRand, excelRandBetween } from "./sheetDateFunctions";
import { coerceNumber, ERROR, numbersFromArgs, type SheetFunctionRegistry } from "./sheetEvalCore";
import { countBlank, countNonBlank, countNumeric } from "./sheetExcelFunctions";
import {
  excelLarge,
  excelLn,
  excelLog,
  excelLog10,
  excelMround,
  excelPercentile,
  excelQuartile,
  excelRank,
  excelSign,
  excelSmall,
  excelTrunc,
  excelVarP,
  excelVarS,
  numbersFromFlat,
} from "./sheetExcelFunctionsPhaseB";
import { excelMedian, excelRoundDown, excelRoundUp, excelStdevS } from "./sheetExcelFunctionsReport";

export const MATH_FUNCTIONS: SheetFunctionRegistry = {
  ABS: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    return Math.abs(coerceNumber(args[0]));
  },

  PRODUCT: (args) => {
    const nums = numbersFromArgs(args);
    if (typeof nums === "string") {
      return nums;
    }
    return nums.reduce((a, b) => a * b, nums.length > 0 ? 1 : 0);
  },

  ROUND: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    const digits = args.length > 1 ? Math.trunc(coerceNumber(args[1])) : 0;
    const factor = 10 ** digits;
    return Math.round(coerceNumber(args[0]) * factor) / factor;
  },

  ROUNDUP: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    return excelRoundUp(
      coerceNumber(args[0]),
      args.length > 1 ? Math.trunc(coerceNumber(args[1])) : 0
    );
  },

  ROUNDDOWN: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    return excelRoundDown(
      coerceNumber(args[0]),
      args.length > 1 ? Math.trunc(coerceNumber(args[1])) : 0
    );
  },

  MEDIAN: (args) => {
    const nums = numbersFromArgs(args);
    if (typeof nums === "string") {
      return nums;
    }
    return excelMedian(nums);
  },

  "STDEV.S": (args) => {
    const nums = numbersFromArgs(args);
    if (typeof nums === "string") {
      return nums;
    }
    return excelStdevS(nums);
  },

  MOD: (args) => {
    if (args.length < 2) {
      return ERROR.value;
    }
    const divisor = coerceNumber(args[1]);
    if (divisor === 0) {
      return ERROR.div0;
    }
    return coerceNumber(args[0]) % divisor;
  },

  POWER: (args) => {
    if (args.length < 2) {
      return ERROR.value;
    }
    return coerceNumber(args[0]) ** coerceNumber(args[1]);
  },

  SQRT: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    const n = coerceNumber(args[0]);
    if (n < 0) {
      return ERROR.value;
    }
    return Math.sqrt(n);
  },

  INT: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    return Math.trunc(coerceNumber(args[0]));
  },

  CEILING: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    return Math.ceil(coerceNumber(args[0]));
  },

  FLOOR: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    return Math.floor(coerceNumber(args[0]));
  },

  RAND: () => {
    return excelRand();
  },

  RANDBETWEEN: (args) => {
    if (args.length < 2) {
      return ERROR.value;
    }
    const value = excelRandBetween(coerceNumber(args[0]), coerceNumber(args[1]));
    if (!Number.isFinite(value)) {
      return ERROR.value;
    }
    return value;
  },

  PI: () => {
    return Math.PI;
  },

  EXP: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    return Math.exp(coerceNumber(args[0]));
  },

  LN: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    return excelLn(coerceNumber(args[0]));
  },

  LOG10: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    return excelLog10(coerceNumber(args[0]));
  },

  LOG: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    const base = args.length > 1 ? coerceNumber(args[1]) : undefined;
    return excelLog(coerceNumber(args[0]), base);
  },

  SIGN: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    return excelSign(coerceNumber(args[0]));
  },

  TRUNC: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    return excelTrunc(
      coerceNumber(args[0]),
      args.length > 1 ? Math.trunc(coerceNumber(args[1])) : 0
    );
  },

  MROUND: (args) => {
    if (args.length < 2) {
      return ERROR.value;
    }
    return excelMround(coerceNumber(args[0]), coerceNumber(args[1]));
  },

  "VAR.S": (args) => {
    const nums = numbersFromFlat(args);
    return excelVarS(nums);
  },

  VAR: (args) => {
    const nums = numbersFromFlat(args);
    return excelVarP(nums);
  },

  PERCENTILE: (args) => {
    const nums = numbersFromFlat(args.slice(0, -1));
    if (args.length < 2) {
      return ERROR.value;
    }
    return excelPercentile(nums, coerceNumber(args[args.length - 1]));
  },

  QUARTILE: (args) => {
    const nums = numbersFromFlat(args.slice(0, -1));
    if (args.length < 2) {
      return ERROR.value;
    }
    return excelQuartile(nums, coerceNumber(args[args.length - 1]));
  },

  LARGE: (args) => {
    const nums = numbersFromFlat(args.slice(0, -1));
    if (args.length < 2) {
      return ERROR.value;
    }
    return excelLarge(nums, coerceNumber(args[args.length - 1]));
  },

  SMALL: (args) => {
    const nums = numbersFromFlat(args.slice(0, -1));
    if (args.length < 2) {
      return ERROR.value;
    }
    return excelSmall(nums, coerceNumber(args[args.length - 1]));
  },

  RANK: (args) => {
    if (args.length < 2) {
      return ERROR.value;
    }
    const number = coerceNumber(args[0]);
    if (args.length === 2) {
      return excelRank(number, numbersFromFlat(args.slice(1)), 0);
    }
    const order = Math.trunc(coerceNumber(args[args.length - 1]));
    return excelRank(number, numbersFromFlat(args.slice(1, -1)), order);
  },

  SIN: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    return Math.sin(coerceNumber(args[0]));
  },

  COS: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    return Math.cos(coerceNumber(args[0]));
  },

  TAN: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    return Math.tan(coerceNumber(args[0]));
  },

  ASIN: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    const n = coerceNumber(args[0]);
    if (n < -1 || n > 1) {
      return ERROR.num;
    }
    return Math.asin(n);
  },

  ACOS: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    const n = coerceNumber(args[0]);
    if (n < -1 || n > 1) {
      return ERROR.num;
    }
    return Math.acos(n);
  },

  ATAN: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    return Math.atan(coerceNumber(args[0]));
  },

  ATAN2: (args) => {
    if (args.length < 2) {
      return ERROR.value;
    }
    return Math.atan2(coerceNumber(args[0]), coerceNumber(args[1]));
  },

  RADIANS: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    return (coerceNumber(args[0]) * Math.PI) / 180;
  },

  DEGREES: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    return (coerceNumber(args[0]) * 180) / Math.PI;
  },

  COUNT: (args) => {
    return countNumeric(args);
  },

  COUNTA: (args) => {
    return countNonBlank(args);
  },

  COUNTBLANK: (args) => {
    return countBlank(args);
  },
};

MATH_FUNCTIONS["STDEV"] = MATH_FUNCTIONS["STDEV.S"];
MATH_FUNCTIONS["VARS"] = MATH_FUNCTIONS["VAR.S"];
MATH_FUNCTIONS["VAR.P"] = MATH_FUNCTIONS["VAR"];
MATH_FUNCTIONS["VARP"] = MATH_FUNCTIONS["VAR"];
