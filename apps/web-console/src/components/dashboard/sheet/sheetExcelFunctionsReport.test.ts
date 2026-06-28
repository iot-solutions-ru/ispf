import { describe, expect, it } from "vitest";
import { evaluateSheetFormula, type SheetEvalEnvironment } from "./ispfSheetEval";
import {
  excelCountifs,
  excelMedian,
  excelRoundUp,
  excelStdevS,
  excelSumifs,
  excelSumproduct,
  excelXlookup,
} from "./sheetExcelFunctionsReport";
import { excelNetworkdays, excelWorkday, excelDate } from "./sheetDateFunctions";
import { findUnsupportedFunctions } from "./sheetFormulaNormalize";

function env(values: Record<string, number | string>): SheetEvalEnvironment {
  return {
    getCell: (address) => values[address.toUpperCase()] ?? values[address] ?? 0,
    ispf: {
      bindingValues: new Map(),
      histValues: new Map(),
      tableColumnSums: new Map(),
    },
  };
}

describe("sheetExcelFunctionsReport", () => {
  it("supports phase-A function names in registry", () => {
    expect(findUnsupportedFunctions("=XLOOKUP(A1,B1:B5,C1:C5)")).toEqual([]);
    expect(findUnsupportedFunctions("=SUMIFS(D1:D5,A1:A5,\"x\")")).toEqual([]);
    expect(findUnsupportedFunctions("=IFS(A1>0,1,A1<0,-1)")).toEqual([]);
  });

  it("evaluates XLOOKUP exact match", () => {
    expect(
      excelXlookup("b", ["a", "b", "c"], [10, 20, 30])
    ).toBe(20);
    expect(
      evaluateSheetFormula("=XLOOKUP(\"b\",A1:A3,B1:B3)", env({ A1: "a", A2: "b", A3: "c", B1: 10, B2: 20, B3: 30 }))
    ).toBe(20);
  });

  it("evaluates SUMIFS and COUNTIFS", () => {
    expect(
      excelSumifs(
        [1, 2, 3],
        [["x", "y", "x"]],
        ["x"]
      )
    ).toBe(4);
    expect(
      excelCountifs(
        [["x", "y", "x"], [1, 2, 1]],
        ["x", ">0"]
      )
    ).toBe(2);
    expect(
      evaluateSheetFormula('=SUMIFS(C1:C3,A1:A3,"x")', env({ A1: "x", A2: "y", A3: "x", C1: 1, C2: 2, C3: 3 }))
    ).toBe(4);
  });

  it("evaluates SUMPRODUCT, MEDIAN, STDEV.S, rounding", () => {
    expect(excelSumproduct([[2, 3], [4, 5]])).toBe(2 * 4 + 3 * 5);
    expect(excelMedian([1, 9, 3])).toBe(3);
    expect(excelStdevS([2, 4, 4, 4, 5, 5, 7, 9])).toBeCloseTo(2.138, 2);
    expect(excelRoundUp(1.234, 2)).toBe(1.24);
    expect(evaluateSheetFormula("=ROUNDUP(1.234,2)", env({}))).toBe(1.24);
    expect(evaluateSheetFormula("=MEDIAN(A1:A3)", env({ A1: 1, A2: 9, A3: 3 }))).toBe(3);
  });

  it("evaluates IFS, TEXTJOIN, SUBTOTAL", () => {
    expect(evaluateSheetFormula("=IFS(A1>10,\"high\",A1<=10,\"low\")", env({ A1: 15 }))).toBe("high");
    expect(evaluateSheetFormula('=TEXTJOIN(\",\",TRUE,A1,A2,B1)', env({ A1: "a", A2: "", B1: "b" }))).toBe("a,b");
    expect(evaluateSheetFormula("=SUBTOTAL(9,A1:A3)", env({ A1: 1, A2: 2, A3: 3 }))).toBe(6);
  });

  it("evaluates NETWORKDAYS and WORKDAY", () => {
    const mon = excelDate(2024, 6, 3);
    const fri = excelDate(2024, 6, 7);
    expect(excelNetworkdays(mon, fri)).toBe(5);
    expect(excelWorkday(mon, 4)).toBe(fri);
    expect(evaluateSheetFormula("=NETWORKDAYS(A1,B1)", env({ A1: mon, B1: fri }))).toBe(5);
  });
});
