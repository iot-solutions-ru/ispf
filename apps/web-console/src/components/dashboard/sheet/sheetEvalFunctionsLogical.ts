// Logical functions of the sheet formula evaluator (args already evaluated).
import { coerceBool, coerceNumber, ERROR, isSheetError, type SheetFunctionRegistry } from "./sheetEvalCore";
import { excelChoose, excelIfna, excelSwitch } from "./sheetExcelFunctionsPhaseB";

/** Functions that see raw errors in their arguments (no error propagation before the call). */
export const LOGICAL_ERROR_TOLERANT_FUNCTIONS: SheetFunctionRegistry = {
  IFERROR: (args) => {
    if (args.length < 2) {
      return ERROR.value;
    }
    const primary = args[0];
    if (isSheetError(primary)) {
      return args[1];
    }
    return primary;
  },

  IFNA: (args) => {
    if (args.length < 2) {
      return ERROR.value;
    }
    return excelIfna(args[0], args[1]);
  },
};

export const LOGICAL_FUNCTIONS: SheetFunctionRegistry = {
  AND: (args) => {
    return args.every((arg) => coerceBool(arg));
  },

  OR: (args) => {
    return args.some((arg) => coerceBool(arg));
  },

  NOT: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    return !coerceBool(args[0]);
  },

  IF: (args) => {
    if (args.length < 2) {
      return ERROR.value;
    }
    return coerceBool(args[0]) ? args[1] : args[2] ?? false;
  },

  TRUE: () => {
    return true;
  },

  FALSE: () => {
    return false;
  },

  ISBLANK: (args) => {
    const v = args[0];
    return v === null || v === "" || v === undefined;
  },

  ISNUMBER: (args) => {
    const v = args[0];
    return typeof v === "number" && Number.isFinite(v);
  },

  ISTEXT: (args) => {
    const v = args[0];
    return typeof v === "string" && v !== "";
  },

  ISERROR: (args) => {
    return isSheetError(args[0]);
  },

  ISNA: (args) => {
    return args[0] === ERROR.na;
  },

  ISERR: (args) => {
    return isSheetError(args[0]) && args[0] !== ERROR.na;
  },

  NA: () => {
    return ERROR.na;
  },

  ISLOGICAL: (args) => {
    return typeof args[0] === "boolean";
  },

  ISODD: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    const n = Math.trunc(coerceNumber(args[0]));
    return Math.abs(n % 2) === 1;
  },

  ISEVEN: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    return Math.trunc(coerceNumber(args[0])) % 2 === 0;
  },

  SWITCH: (args) => {
    if (args.length < 3) {
      return ERROR.value;
    }
    return excelSwitch(args[0], args.slice(1));
  },

  CHOOSE: (args) => {
    if (args.length < 2) {
      return ERROR.value;
    }
    return excelChoose(Math.trunc(coerceNumber(args[0])), args.slice(1));
  },
};
