import { describe, expect, it } from "vitest";
import { createSheetFormulaEngine } from "./sheetFormulaEngine";
import { evaluateSheetFormula, type SheetEvalEnvironment } from "./ispfSheetEval";

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

describe("basic spreadsheet formulas", () => {
  it("evaluates growth percent arithmetic", () => {
    expect(evaluateSheetFormula("=E3-B3", env({ E3: 120, B3: 100 }))).toBe(20);
    expect(evaluateSheetFormula("=E3/B3", env({ E3: 120, B3: 100 }))).toBe(1.2);
    expect(evaluateSheetFormula("=(E3-B3)", env({ E3: 120, B3: 100 }))).toBe(20);
    expect(evaluateSheetFormula("=(E3)/B3", env({ E3: 120, B3: 100 }))).toBe(1.2);
    expect(evaluateSheetFormula("=(E3-B3)/B3", env({ E3: 120, B3: 100 }))).toBe(0.2);
  });

  it("evaluates AVERAGE over a range", () => {
    expect(
      evaluateSheetFormula("=AVERAGE(H2:H6)", env({ H2: 10, H3: 20, H4: 30, H5: 40, H6: 50 }))
    ).toBe(30);
  });

  it("evaluates through formula engine in free mode", () => {
    const engine = createSheetFormulaEngine(
      { rows: 10, cols: 10, cells: {} },
      "free",
      {
        B3: "100",
        E3: "120",
        H2: "10",
        H3: "20",
        H4: "30",
        H5: "40",
        H6: "50",
        C3: "=(E3-B3)/B3",
        I7: "=AVERAGE(H2:H6)",
      }
    );
    expect(engine.getCellValue("C3")).toBe(0.2);
    expect(engine.getCellValue("I7")).toBe(30);
    engine.destroy();
  });

  it("does not treat imported error literal as formula", () => {
    const engine = createSheetFormulaEngine(
      { rows: 5, cols: 5, cells: {} },
      "free",
      { A1: "#NAME?" }
    );
    expect(engine.getCellValue("A1")).toBe("#NAME?");
    engine.destroy();
  });
});
