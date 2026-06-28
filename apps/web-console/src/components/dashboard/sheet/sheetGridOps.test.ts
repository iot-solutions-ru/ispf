import { describe, expect, it } from "vitest";
import { createDefaultWorkbook } from "./sheetWorkbook";
import { applyGridOperation, shiftFormulaForGridOp } from "./sheetGridOps";

describe("sheetGridOps", () => {
  it("shifts local formula refs when inserting a row", () => {
    expect(shiftFormulaForGridOp("=A5+B10", "row", 2, 1, "insert", "Sheet1", "Sheet1")).toBe("=A6+B11");
  });

  it("shifts cross-sheet refs on other sheets when active sheet rows change", () => {
    expect(
      shiftFormulaForGridOp("=Sales!A5", "row", 2, 1, "insert", "Sales", "Summary")
    ).toBe("=Sales!A6");
  });

  it("does not shift cross-sheet refs targeting another sheet", () => {
    expect(
      shiftFormulaForGridOp("=Sales!A5", "row", 2, 1, "insert", "Summary", "Summary")
    ).toBe("=Sales!A5");
  });

  it("moves cell values down on row insert", () => {
    const wb = createDefaultWorkbook(5, 3);
    wb.sheets[0].contents = { A3: "x", B5: "=A3" };
    const next = applyGridOperation(wb, 0, { axis: "row", mode: "insert", at: 2 });
    expect(next.sheets[0].rows).toBe(6);
    expect(next.sheets[0].contents.A4).toBe("x");
    expect(next.sheets[0].contents.B6).toBe("=A4");
    expect(next.sheets[0].contents.A3).toBeUndefined();
  });

  it("deletes row and marks refs on deleted row as #REF!", () => {
    const wb = createDefaultWorkbook(5, 3);
    wb.sheets[0].contents = { A3: "x", B3: "=A3", A4: "=A3" };
    const next = applyGridOperation(wb, 0, { axis: "row", mode: "delete", at: 2 });
    expect(next.sheets[0].rows).toBe(4);
    expect(next.sheets[0].contents.A3).toBe("=#REF!");
    expect(next.sheets[0].contents.B3).toBeUndefined();
  });

  it("inserts column and shifts SUM range", () => {
    const wb = createDefaultWorkbook(5, 4);
    wb.sheets[0].contents = { A1: "1", B1: "2", D1: "=SUM(A1:B1)" };
    const next = applyGridOperation(wb, 0, { axis: "col", mode: "insert", at: 1 });
    expect(next.sheets[0].cols).toBe(5);
    expect(next.sheets[0].contents.A1).toBe("1");
    expect(next.sheets[0].contents.C1).toBe("2");
    expect(next.sheets[0].contents.E1).toBe("=SUM(A1:C1)");
  });
});
