import { describe, expect, it } from "vitest";
import { evaluateSheetFormula, type SheetEvalEnvironment } from "./ispfSheetEval";
import type { IspfFormulaContext } from "./sheetFormulaEngineContext";

/**
 * Characterization suite for the scalar function registry (F-04 split of ispfSheetEval.ts).
 * One formula per function; expected values were captured from the pre-split if-chain
 * implementation, so any drift in dispatch, aliasing, error tolerance or coercion shows up here.
 */
const CELLS: Record<string, number | string | boolean | null> = {
  A1: 10,
  A2: 20,
  A3: 30,
  A4: "",
  A5: "text",
  A6: true,
  B1: 2.5,
  B2: -7,
  B3: 0,
  B4: "#N/A",
  B5: "#DIV/0!",
};

const ispf: IspfFormulaContext = {
  bindingValues: new Map([["root.devices.pump|speed|value", 42]]),
  tableColumnSums: new Map([["orders|amount", 6]]),
  histValues: new Map([["root.devices.pump|speed|60", 7]]),
};

const env: SheetEvalEnvironment = {
  getCell: (address) => (address in CELLS ? CELLS[address] : null),
  originCell: "C7",
  ispf,
};

// [formula, expected] — expected captured from the pre-split implementation (quirks included, e.g.
// ISERROR/ISNA propagate the error because propagation runs before dispatch); numbers ±1e-9
const CASES: Array<[string, number | string | boolean | null]> = [
  // logical / info
  ["=IF(A1>5,\"big\",\"small\")", "big"],
  ["=IF(A1>50,\"big\")", false],
  ["=AND(A6,A1>5)", true],
  ["=OR(A1>50,A6)", true],
  ["=NOT(A6)", false],
  ["=TRUE()", "#VALUE!"],
  ["=FALSE()", "#VALUE!"],
  ["=IFERROR(B4,\"fallback\")", "fallback"],
  ["=IFERROR(A1,\"fallback\")", 10],
  ["=IFNA(B4,\"na\")", "na"],
  ["=IFNA(B5,\"na\")", "#DIV/0!"],
  ["=ISBLANK(A4)", true],
  ["=ISBLANK(A1)", false],
  ["=ISNUMBER(A1)", true],
  ["=ISNUMBER(A5)", false],
  ["=ISTEXT(A5)", true],
  ["=ISLOGICAL(A6)", true],
  ["=ISODD(3)", true],
  ["=ISEVEN(3)", false],
  ["=ISERROR(B5)", "#DIV/0!"],
  ["=ISNA(B4)", "#N/A"],
  ["=ISERR(B5)", "#DIV/0!"],
  ["=ISERR(B4)", "#N/A"],
  ["=ISNA(NA())", "#N/A"],
  ["=SWITCH(2,1,\"one\",2,\"two\",\"other\")", "two"],
  ["=CHOOSE(3,\"a\",\"b\",\"c\")", "c"],
  // math
  ["=ABS(B2)", 7],
  ["=PRODUCT(A1,A2,B1)", 500],
  ["=ROUND(2.345,2)", 2.35],
  ["=ROUNDUP(2.341,2)", 2.35],
  ["=ROUNDDOWN(2.349,2)", 2.34],
  ["=MEDIAN(A1,A2,A3,100)", 25],
  ["=STDEV.S(A1,A2,A3)", 10],
  ["=STDEV(A1,A2,A3)", 10],
  ["=VAR.S(A1,A2,A3)", 100],
  ["=VARS(A1,A2,A3)", 100],
  ["=VAR(A1,A2,A3)", 66.66666666666667],
  ["=VAR.P(A1,A2,A3)", 66.66666666666667],
  ["=VARP(A1,A2,A3)", 66.66666666666667],
  ["=MOD(17,5)", 2],
  ["=MOD(5,0)", "#DIV/0!"],
  ["=POWER(2,10)", 1024],
  ["=SQRT(81)", 9],
  ["=SQRT(-1)", "#VALUE!"],
  ["=INT(-2.5)", -2],
  ["=CEILING(4.2,1)", 5],
  ["=FLOOR(4.8,1)", 4],
  ["=PI()", 3.141592653589793],
  ["=EXP(0)", 1],
  ["=LN(EXP(2))", 2],
  ["=LOG10(1000)", "#VALUE!"],
  ["=LOG(8,2)", 3],
  ["=SIGN(B2)", -1],
  ["=TRUNC(8.97,1)", 8.9],
  ["=MROUND(13,5)", 15],
  ["=PERCENTILE(A1,0.5)", 10],
  ["=QUARTILE(A1,2)", 10],
  ["=LARGE(A1,1)", 10],
  ["=SMALL(A1,1)", 10],
  ["=RANK(A2,A1)", "#N/A"],
  ["=SIN(0)", 0],
  ["=COS(0)", 1],
  ["=TAN(0)", 0],
  ["=ASIN(1)", 1.5707963267948966],
  ["=ACOS(1)", 0],
  ["=ATAN(1)", 0.7853981633974483],
  ["=ATAN2(1,1)", "#VALUE!"],
  ["=RADIANS(180)", 3.141592653589793],
  ["=DEGREES(PI())", 180],
  ["=COUNT(A1,A5,A2)", 2],
  ["=COUNTA(A1,A4,A5)", 2],
  ["=COUNTBLANK(A1,A4)", 1],
  ["=SUM(A1,A2,A3)", 60],
  ["=AVERAGE(A1,A2,A3)", 20],
  ["=AVG(A1,A2)", 15],
  ["=MIN(A1,B2)", -7],
  ["=MAX(A1,B1)", 10],
  ["=SUM(A1,B4)", "#N/A"],
  // text
  ["=LEN(\"hello\")", 5],
  ["=LEFT(\"hello\",2)", "he"],
  ["=RIGHT(\"hello\",3)", "llo"],
  ["=MID(\"hello\",2,3)", "ell"],
  ["=TRIM(\"  a  b  \")", "a  b"],
  ["=UPPER(\"abc\")", "ABC"],
  ["=LOWER(\"ABC\")", "abc"],
  ["=PROPER(\"hello world\")", "Hello World"],
  ["=CHAR(65)", "A"],
  ["=CODE(\"A\")", 65],
  ["=REPT(\"ab\",3)", "ababab"],
  ["=CONCAT(\"a\",1,TRUE())", "#VALUE!"],
  ["=CONCATENATE(\"x\",\"y\")", "xy"],
  ["=TEXT(1234.5,\"0.00\")", "1234.50"],
  ["=CLEAN(\"a\" & CHAR(10) & \"b\")", "a\nb"],
  ["=FIND(\"l\",\"hello\")", 3],
  ["=SEARCH(\"L\",\"hello\")", 3],
  ["=SUBSTITUTE(\"aaa\",\"a\",\"b\",2)", "aba"],
  ["=REPLACE(\"hello\",1,1,\"j\")", "jello"],
  ["=VALUE(\"12.5\")", 12.5],
  ["=EXACT(\"a\",\"A\")", false],
  // date / time (serials, deterministic)
  ["=DATE(2024,3,15)", 45366],
  ["=YEAR(45366)", 2024],
  ["=MONTH(45366)", 3],
  ["=DAY(45366)", 15],
  ["=DAYS(45366,45300)", 66],
  ["=WEEKDAY(45366)", 6],
  ["=HOUR(0.75)", 18],
  ["=MINUTE(0.5104166666666667)", 15],
  ["=SECOND(0.5104166666666667)", 0],
  ["=TIME(18,15,0)", 0.7604166666666666],
  ["=EDATE(45366,1)", 45397],
  ["=EOMONTH(45366,0)", 45382],
  ["=DATEDIF(45300,45366,\"D\")", 66],
  ["=NETWORKDAYS(45366,45373)", 6],
  ["=WORKDAY(45366,1)", 45369],
  ["=DATEVALUE(\"2024-03-15\")", 45366],
  ["=TIMEVALUE(\"18:00\")", 0.75],
  ["=WEEKNUM(45366)", 11],
  ["=YEARFRAC(45292,45658)", 1.0166666666666666],
  ["=ISNUMBER(TODAY())", true],
  ["=ISNUMBER(NOW())", true],
  ["=AND(RAND()>=0,RAND()<1)", true],
  ["=AND(RANDBETWEEN(1,3)>=1,RANDBETWEEN(1,3)<=3)", true],
  // financial
  ["=NPV(0.1,100,100)", 173.55371900826447],
  ["=PMT(0.01,12,-1000)", 88.84878867834166],
  ["=FV(0.01,12,-100)", 1268.2503013196979],
  ["=PV(0.01,12,-100)", 1125.5077473484637],
  ["=NPER(0.01,-100,1000)", 10.58864445942323],
  ["=RATE(12,-100,1000)", 0.02922854076913403],
  ["=IRR(-100,60,60)", "#NUM!"],
  // ispf
  ["=ISPREF(\"root.devices.pump\",\"speed\")", 42],
  ["=ISPSUM(\"orders\",\"amount\")", 6],
  ["=ISPHIST(\"root.devices.pump\",\"speed\",60)", 7],
  ["=ISPHIST(\"root.devices.pump\",\"speed\")", 42],
  // unknown name / propagation
  ["=NOSUCHFN(1)", "#NAME?"],
  ["=ABS(B5)", "#DIV/0!"],
];

describe("sheet function registry (characterization)", () => {
  it.each(CASES)("%s", (formula, expected) => {
    const actual = evaluateSheetFormula(formula, env);
    if (typeof expected === "number") {
      expect(typeof actual).toBe("number");
      expect(actual as number).toBeCloseTo(expected, 9);
    } else {
      expect(actual).toBe(expected);
    }
  });
});
