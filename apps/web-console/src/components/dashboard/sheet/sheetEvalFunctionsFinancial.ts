// Financial functions of the sheet formula evaluator (args already evaluated).
import { coerceNumber, ERROR, type SheetFunctionRegistry } from "./sheetEvalCore";
import {
  excelFv,
  excelIrr,
  excelNper,
  excelNpv,
  excelPmt,
  excelPv,
  excelRate,
  numbersFromFlat,
} from "./sheetExcelFunctionsPhaseB";

export const FINANCIAL_FUNCTIONS: SheetFunctionRegistry = {
  NPV: (args) => {
    if (args.length < 2) {
      return ERROR.value;
    }
    const rate = coerceNumber(args[0]);
    const values = numbersFromFlat(args.slice(1));
    return excelNpv(rate, values);
  },

  PMT: (args) => {
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
  },

  FV: (args) => {
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
  },

  PV: (args) => {
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
  },

  NPER: (args) => {
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
  },

  RATE: (args) => {
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
  },

  IRR: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    if (args.length >= 2) {
      return excelIrr(numbersFromFlat(args.slice(0, -1)), coerceNumber(args[args.length - 1]));
    }
    return excelIrr(numbersFromFlat(args));
  },
};
