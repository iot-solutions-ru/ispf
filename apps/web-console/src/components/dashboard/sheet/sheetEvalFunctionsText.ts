// Text functions of the sheet formula evaluator (args already evaluated).
import { coerceNumber, ERROR, type SheetFunctionRegistry } from "./sheetEvalCore";
import {
  excelExact,
  excelFind,
  excelReplace,
  excelSearch,
  excelSubstitute,
  excelValue,
} from "./sheetExcelFunctionsPhaseB";

export const TEXT_FUNCTIONS: SheetFunctionRegistry = {
  LEN: (args) => {
    return String(args[0] ?? "").length;
  },

  LEFT: (args) => {
    const text = String(args[0] ?? "");
    const n = args.length > 1 ? Math.max(0, Math.trunc(coerceNumber(args[1]))) : 1;
    return text.slice(0, n);
  },

  RIGHT: (args) => {
    const text = String(args[0] ?? "");
    const n = args.length > 1 ? Math.max(0, Math.trunc(coerceNumber(args[1]))) : 1;
    return text.slice(Math.max(0, text.length - n));
  },

  MID: (args) => {
    if (args.length < 3) {
      return ERROR.value;
    }
    const text = String(args[0] ?? "");
    const start = Math.max(1, Math.trunc(coerceNumber(args[1]))) - 1;
    const len = Math.max(0, Math.trunc(coerceNumber(args[2])));
    return text.slice(start, start + len);
  },

  TRIM: (args) => {
    return String(args[0] ?? "").trim();
  },

  UPPER: (args) => {
    return String(args[0] ?? "").toUpperCase();
  },

  LOWER: (args) => {
    return String(args[0] ?? "").toLowerCase();
  },

  PROPER: (args) => {
    return String(args[0] ?? "").replace(/\w+/g, (word) =>
      word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()
    );
  },

  CHAR: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    const code = Math.trunc(coerceNumber(args[0]));
    if (code < 1 || code > 65535) {
      return ERROR.value;
    }
    return String.fromCharCode(code);
  },

  CODE: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    const text = String(args[0] ?? "");
    if (text.length === 0) {
      return ERROR.value;
    }
    return text.charCodeAt(0);
  },

  REPT: (args) => {
    if (args.length < 2) {
      return ERROR.value;
    }
    const text = String(args[0] ?? "");
    const times = Math.trunc(coerceNumber(args[1]));
    if (times < 0) {
      return ERROR.value;
    }
    if (times * text.length > 32767) {
      return ERROR.value;
    }
    return text.repeat(times);
  },

  CONCAT: (args) => {
    return args.map((arg) => String(arg ?? "")).join("");
  },

  TEXT: (args) => {
    if (args.length < 2) {
      return ERROR.value;
    }
    const num = coerceNumber(args[0]);
    const fmt = String(args[1]);
    const decimals = (fmt.match(/0\.(0+)/)?.[1]?.length ?? 0);
    return num.toFixed(decimals);
  },

  CLEAN: (args) => {
    // eslint-disable-next-line no-control-regex -- Excel CLEAN(): strips non-printable ASCII by design
    return String(args[0] ?? "").replace(/[\x00-\x08\x0B\x0C\x0E-\x1F]/g, "");
  },

  FIND: (args) => {
    if (args.length < 2) {
      return ERROR.value;
    }
    const start = args.length > 2 ? coerceNumber(args[2]) : 1;
    return excelFind(args[0], args[1], start);
  },

  SEARCH: (args) => {
    if (args.length < 2) {
      return ERROR.value;
    }
    const start = args.length > 2 ? coerceNumber(args[2]) : 1;
    return excelSearch(args[0], args[1], start);
  },

  SUBSTITUTE: (args) => {
    if (args.length < 3) {
      return ERROR.value;
    }
    const instance = args.length > 3 ? Math.trunc(coerceNumber(args[3])) : undefined;
    return excelSubstitute(args[0], args[1], args[2], instance);
  },

  REPLACE: (args) => {
    if (args.length < 4) {
      return ERROR.value;
    }
    return excelReplace(args[0], coerceNumber(args[1]), coerceNumber(args[2]), args[3]);
  },

  VALUE: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    return excelValue(args[0]);
  },

  EXACT: (args) => {
    if (args.length < 2) {
      return ERROR.value;
    }
    return excelExact(args[0], args[1]);
  },
};

TEXT_FUNCTIONS["CONCATENATE"] = TEXT_FUNCTIONS["CONCAT"];
