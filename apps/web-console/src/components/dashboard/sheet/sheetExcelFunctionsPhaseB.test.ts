import { describe, expect, it } from "vitest";
import { evaluateSheetFormula, type SheetEvalEnvironment } from "./ispfSheetEval";
import {
  excelDatevalue,
  excelFind,
  excelIrr,
  excelLn,
  excelNpv,
  excelPmt,
  excelSearch,
  excelSubstitute,
  excelSwitch,
  excelValue,
  excelVarS,
} from "./sheetExcelFunctionsPhaseB";
import { excelDate } from "./sheetDateFunctions";
import { findUnsupportedFunctions } from "./sheetFormulaNormalize";

function env(
  values: Record<string, number | string>,
  originCell = "A1"
): SheetEvalEnvironment {
  return {
    getCell: (address) => values[address.toUpperCase()] ?? values[address] ?? 0,
    originCell,
    ispf: {
      bindingValues: new Map(),
      histValues: new Map(),
      tableColumnSums: new Map(),
    },
  };
}

describe("sheetExcelFunctionsPhaseB", () => {
  it("supports phase-B function names in registry", () => {
    expect(findUnsupportedFunctions("=LN(10)")).toEqual([]);
    expect(findUnsupportedFunctions("=FIND(\"b\",\"abc\")")).toEqual([]);
    expect(findUnsupportedFunctions("=NPV(0.1,A1:A3)")).toEqual([]);
    expect(findUnsupportedFunctions("=PMT(0.05/12,12,-1000)")).toEqual([]);
    expect(findUnsupportedFunctions("=ROW()")).toEqual([]);
  });

  it("evaluates log and trig functions", () => {
    expect(excelLn(Math.E)).toBeCloseTo(1, 10);
    expect(evaluateSheetFormula("=LN(EXP(1))", env({}))).toBeCloseTo(1, 10);
    expect(evaluateSheetFormula("=LOG(1000,10)", env({}))).toBeCloseTo(3, 10);
    expect(evaluateSheetFormula("=SIN(0)", env({}))).toBe(0);
    expect(evaluateSheetFormula("=DEGREES(PI())", env({}))).toBeCloseTo(180, 10);
  });

  it("evaluates text functions", () => {
    expect(excelFind("b", "abc")).toBe(2);
    expect(excelSearch("B", "abc")).toBe(2);
    expect(excelSubstitute("a-b-b", "b", "x")).toBe("a-x-x");
    expect(excelValue("123.45")).toBe(123.45);
    expect(evaluateSheetFormula('=SUBSTITUTE("a-b","b","x")', env({}))).toBe("a-x");
    expect(evaluateSheetFormula('=EXACT("A","a")', env({}))).toBe(false);
  });

  it("evaluates date parsing and SWITCH/CHOOSE", () => {
    expect(excelDatevalue("2026-06-28")).toBe(excelDate(2026, 6, 28));
    expect(excelSwitch(2, [1, "one", 2, "two"])).toBe("two");
    expect(evaluateSheetFormula('=CHOOSE(2,"a","b","c")', env({}))).toBe("b");
    expect(
      evaluateSheetFormula('=IFNA(XLOOKUP(99,A1:A2,B1:B2),"ok")', env({ A1: 1, A2: 2, B1: 10, B2: 20 }))
    ).toBe("ok");
  });

  it("evaluates stats and finance", () => {
    expect(excelVarS([2, 4, 4, 4, 5, 5, 7, 9])).toBeCloseTo(4.571, 2);
    expect(excelNpv(0.1, [-100, 50, 60])).toBeCloseTo(-4.51, 1);
    const pmt = excelPmt(0.05 / 12, 12, -1000);
    expect(typeof pmt).toBe("number");
    expect(Math.abs(pmt as number)).toBeCloseTo(85.61, 1);
    const flows = [-1000, 300, 420, 680];
    const irr = excelIrr(flows);
    expect(typeof irr).toBe("number");
    expect(irr as number).toBeCloseTo(0.163, 2);
  });

  it("evaluates ROW/COLUMN/ROWS/COLUMNS", () => {
    expect(evaluateSheetFormula("=ROW()", env({}, "C5"))).toBe(5);
    expect(evaluateSheetFormula("=COLUMN()", env({}, "C5"))).toBe(3);
    expect(evaluateSheetFormula("=ROW(B10)", env({}))).toBe(10);
    expect(evaluateSheetFormula("=ROWS(A1:C3)", env({}))).toBe(3);
    expect(evaluateSheetFormula("=COLUMNS(A1:C3)", env({}))).toBe(3);
  });
});
