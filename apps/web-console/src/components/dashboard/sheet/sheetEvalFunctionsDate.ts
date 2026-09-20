// Date functions of the sheet formula evaluator (args already evaluated).
import {
  excelDate,
  excelDatedif,
  excelDay,
  excelDays,
  excelEdate,
  excelEomonth,
  excelHour,
  excelMinute,
  excelMonth,
  excelNetworkdays,
  excelNowSerial,
  excelSecond,
  excelTime,
  excelTodaySerial,
  excelWeekday,
  excelWorkday,
  excelYear,
} from "./sheetDateFunctions";
import { coerceNumber, ERROR, type SheetFunctionRegistry } from "./sheetEvalCore";
import { excelDatevalue, excelTimevalue, excelWeeknum, excelYearfrac } from "./sheetExcelFunctionsPhaseB";

export const DATE_FUNCTIONS: SheetFunctionRegistry = {
  TODAY: () => {
    return excelTodaySerial();
  },

  NOW: () => {
    return excelNowSerial();
  },

  YEAR: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    return excelYear(coerceNumber(args[0]));
  },

  MONTH: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    return excelMonth(coerceNumber(args[0]));
  },

  DAY: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    return excelDay(coerceNumber(args[0]));
  },

  DATE: (args) => {
    if (args.length < 3) {
      return ERROR.value;
    }
    return excelDate(coerceNumber(args[0]), coerceNumber(args[1]), coerceNumber(args[2]));
  },

  DAYS: (args) => {
    if (args.length < 2) {
      return ERROR.value;
    }
    return excelDays(coerceNumber(args[0]), coerceNumber(args[1]));
  },

  WEEKDAY: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    const returnType = args.length > 1 ? Math.trunc(coerceNumber(args[1])) : 1;
    return excelWeekday(coerceNumber(args[0]), returnType);
  },

  HOUR: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    return excelHour(coerceNumber(args[0]));
  },

  MINUTE: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    return excelMinute(coerceNumber(args[0]));
  },

  SECOND: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    return excelSecond(coerceNumber(args[0]));
  },

  TIME: (args) => {
    if (args.length < 3) {
      return ERROR.value;
    }
    return excelTime(coerceNumber(args[0]), coerceNumber(args[1]), coerceNumber(args[2]));
  },

  EDATE: (args) => {
    if (args.length < 2) {
      return ERROR.value;
    }
    return excelEdate(coerceNumber(args[0]), coerceNumber(args[1]));
  },

  EOMONTH: (args) => {
    if (args.length < 2) {
      return ERROR.value;
    }
    return excelEomonth(coerceNumber(args[0]), coerceNumber(args[1]));
  },

  DATEDIF: (args) => {
    if (args.length < 3) {
      return ERROR.value;
    }
    return excelDatedif(
      coerceNumber(args[0]),
      coerceNumber(args[1]),
      String(args[2] ?? "D")
    );
  },

  NETWORKDAYS: (args) => {
    if (args.length < 2) {
      return ERROR.value;
    }
    const holidays = args.slice(2).map((arg) => Math.trunc(coerceNumber(arg)));
    return excelNetworkdays(coerceNumber(args[0]), coerceNumber(args[1]), holidays);
  },

  WORKDAY: (args) => {
    if (args.length < 2) {
      return ERROR.value;
    }
    const holidays = args.slice(2).map((arg) => Math.trunc(coerceNumber(arg)));
    return excelWorkday(coerceNumber(args[0]), coerceNumber(args[1]), holidays);
  },

  DATEVALUE: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    return excelDatevalue(args[0]);
  },

  TIMEVALUE: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    return excelTimevalue(args[0]);
  },

  WEEKNUM: (args) => {
    if (args.length < 1) {
      return ERROR.value;
    }
    const returnType = args.length > 1 ? Math.trunc(coerceNumber(args[1])) : 1;
    return excelWeeknum(coerceNumber(args[0]), returnType);
  },

  YEARFRAC: (args) => {
    if (args.length < 2) {
      return ERROR.value;
    }
    const basis = args.length > 2 ? Math.trunc(coerceNumber(args[2])) : 0;
    return excelYearfrac(coerceNumber(args[0]), coerceNumber(args[1]), basis);
  },
};
