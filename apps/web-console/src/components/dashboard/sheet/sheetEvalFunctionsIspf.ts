// Ispf functions of the sheet formula evaluator (args already evaluated).
import { coerceNumber, ERROR, type SheetFunctionRegistry } from "./sheetEvalCore";
import { bindingCacheKey, histCacheKey } from "./sheetFormulaEngineContext";

export const ISPF_FUNCTIONS: SheetFunctionRegistry = {
  ISPREF: (args, env) => {
    if (args.length < 2) {
      return ERROR.value;
    }
    const path = String(args[0]);
    const varName = String(args[1]);
    const field = args[2] !== undefined ? String(args[2]) : "value";
    const v = env.ispf.bindingValues.get(bindingCacheKey(path, varName, field));
    return coerceNumber(v);
  },

  ISPSUM: (args, env) => {
    if (args.length < 2) {
      return ERROR.value;
    }
    const tableVar = String(args[0]);
    const column = String(args[1]);
    return env.ispf.tableColumnSums.get(`${tableVar}|${column}`) ?? 0;
  },

  ISPHIST: (args, env) => {
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
  },
};
