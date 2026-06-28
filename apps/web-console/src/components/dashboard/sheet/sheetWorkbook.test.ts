import { describe, expect, it } from "vitest";
import {
  extractContentsForSheet,
  qualifyCellRef,
  workbookFromPersist,
  workbookToPersist,
} from "./sheetWorkbook";
import type { SheetRuntimeMeta } from "./sheetRuntimeMeta";

describe("sheetWorkbook", () => {
  it("round-trips multi-sheet workbook through qualified cell refs", () => {
    const meta: SheetRuntimeMeta = {
      activeSheetIndex: 1,
      sheets: [
        { name: "Sales", rows: 10, cols: 4 },
        { name: "Summary", rows: 6, cols: 3 },
      ],
    };
    const values = {
      [qualifyCellRef("Sales", "A1")]: "100",
      [qualifyCellRef("Summary", "B2")]: "=SUM(Sales!A1:A10)",
    };

    const loaded = workbookFromPersist(values, meta, 12, 8);
    expect(loaded.activeSheetIndex).toBe(1);
    expect(loaded.sheets).toHaveLength(2);
    expect(loaded.sheets[0].contents.A1).toBe("100");
    expect(loaded.sheets[1].contents.B2).toBe("=SUM(Sales!A1:A10)");

    const persisted = workbookToPersist(loaded);
    expect(persisted.values).toEqual(values);
    expect(persisted.meta.sheets).toHaveLength(2);
    expect(persisted.meta.activeSheetIndex).toBe(1);
  });

  it("extracts sheet-local addresses from qualified refs", () => {
    const values = {
      "Sales!A1": "x",
      "Summary!C3": "y",
      A1: "legacy",
    };
    expect(extractContentsForSheet(values, "Sales")).toEqual({ A1: "x" });
    expect(extractContentsForSheet(values, "Summary")).toEqual({ C3: "y" });
  });
});
