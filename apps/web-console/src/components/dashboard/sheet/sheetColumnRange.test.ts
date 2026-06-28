import { describe, expect, it } from "vitest";
import { evaluateSheetFormula, type SheetEvalEnvironment } from "./ispfSheetEval";

function crossSheetEnv(): SheetEvalEnvironment {
  const cells: Record<string, number | string> = {
    "Данные!C1": "Тип",
    "Данные!C2": "Доход",
    "Данные!D2": 100,
    "Данные!C3": "Расход",
    "Данные!D3": 40,
    "Данные!C4": "Доход",
    "Данные!D4": 250,
  };
  return {
    getCell: (ref) => {
      const key = Object.keys(cells).find((k) => k.toLowerCase() === ref.toLowerCase());
      return key ? cells[key] : 0;
    },
    defaultSheet: "Отчет",
    getSheetBounds: (sheetName) =>
      sheetName?.toLowerCase() === "данные" ? { rows: 10, cols: 5 } : { rows: 10, cols: 5 },
    ispf: {
      bindingValues: new Map(),
      histValues: new Map(),
      tableColumnSums: new Map(),
    },
  };
}

describe("whole-column range refs", () => {
  it("evaluates SUMIFS with Sheet!D:D cross-sheet refs", () => {
    expect(
      evaluateSheetFormula('=SUMIFS(Данные!D:D, Данные!C:C, "Доход")', crossSheetEnv())
    ).toBe(350);
  });

  it("evaluates SUMIFS with bounded column refs D1:D10", () => {
    expect(
      evaluateSheetFormula('=SUMIFS(D1:D10, C1:C10, "Доход")', {
        ...crossSheetEnv(),
        getCell: (ref) => {
          const cells: Record<string, number | string> = {
            C2: "Доход",
            D2: 100,
            C4: "Доход",
            D4: 250,
          };
          return cells[ref.toUpperCase()] ?? 0;
        },
        defaultSheet: undefined,
        getSheetBounds: () => ({ rows: 10, cols: 5 }),
      })
    ).toBe(350);
  });
});
