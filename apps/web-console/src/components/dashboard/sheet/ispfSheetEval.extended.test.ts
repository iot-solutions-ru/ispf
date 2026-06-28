import { describe, expect, it } from "vitest";
import { evaluateSheetFormula, type SheetEvalEnvironment } from "./ispfSheetEval";

function env(values: Record<string, number | string>): SheetEvalEnvironment {
  return {
    getCell: (address) => values[address] ?? 0,
    ispf: {
      bindingValues: new Map(),
      histValues: new Map(),
      tableColumnSums: new Map(),
    },
  };
}

describe("ispfSheetEval extended functions", () => {
  it("evaluates Russian SUM with semicolon separators", () => {
    const result = evaluateSheetFormula("=СУММ(A1;A2)", env({ A1: 2, A2: 3 }));
    expect(result).toBe(5);
  });

  it("evaluates ABS, PRODUCT, AND, OR, NOT", () => {
    expect(evaluateSheetFormula("=ABS(-4)", env({}))).toBe(4);
    expect(evaluateSheetFormula("=PRODUCT(2,3,4)", env({}))).toBe(24);
    expect(evaluateSheetFormula("=AND(TRUE,FALSE)", env({}))).toBe(false);
    expect(evaluateSheetFormula("=OR(FALSE,TRUE)", env({}))).toBe(true);
    expect(evaluateSheetFormula("=NOT(FALSE)", env({}))).toBe(true);
  });

  it("evaluates VLOOKUP and string concat operator", () => {
    const table = env({
      A1: "A",
      B1: 10,
      A2: "B",
      B2: 20,
    });
    expect(evaluateSheetFormula("=VLOOKUP(\"B\",A1:B2,2,FALSE)", table)).toBe(20);
    expect(evaluateSheetFormula('="Hi "&"there"', env({}))).toBe("Hi there");
  });

  it("evaluates SUMIF and COUNTIF", () => {
    const values = env({
      A1: "x",
      A2: "y",
      A3: "x",
      B1: 1,
      B2: 2,
      B3: 3,
    });
    expect(evaluateSheetFormula('=SUMIF(A1:A3,"x",B1:B3)', values)).toBe(4);
    expect(evaluateSheetFormula('=COUNTIF(A1:A3,"x")', values)).toBe(2);
  });

  it("evaluates Excel date and random functions", () => {
    const now = new Date();
    const sheet = env({});
    expect(evaluateSheetFormula("=YEAR(TODAY())", sheet)).toBe(now.getFullYear());
    expect(evaluateSheetFormula("=MONTH(TODAY())", sheet)).toBe(now.getMonth() + 1);
    expect(evaluateSheetFormula("=DAY(TODAY())", sheet)).toBe(now.getDate());
    expect(evaluateSheetFormula("=DATE(2027,1,1)-TODAY()", sheet)).toBeGreaterThan(0);
    expect(evaluateSheetFormula("=RAND()", sheet)).toBeGreaterThanOrEqual(0);
    expect(evaluateSheetFormula("=RAND()", sheet)).toBeLessThan(1);
    const between = evaluateSheetFormula("=RANDBETWEEN(1,100)", sheet);
    expect(between).toBeGreaterThanOrEqual(1);
    expect(between).toBeLessThanOrEqual(100);
  });
});
