import { describe, expect, it } from "vitest";
import { exportXlsxWorkbook, exportXlsxWorkbookFromTabs } from "./sheetXlsx";

describe("sheetXlsx export (BL-150)", () => {
  it("exports single-sheet workbook blob", async () => {
    const contents: Record<string, string | number> = {
      A1: 10,
      B1: "=A1*2",
    };
    const blob = await exportXlsxWorkbook({
      sheetName: "Sheet1",
      rows: 5,
      cols: 5,
      getCellEditContent: (addr) => String(contents[addr] ?? ""),
      getCellValue: (addr) => contents[addr] ?? "",
    });
    expect(blob).toBeInstanceOf(Blob);
    expect(blob.size).toBeGreaterThan(100);
  });

  it("exports multi-sheet workbook with cross-sheet refs", async () => {
    const blob = await exportXlsxWorkbookFromTabs({
      workbook: {
        activeSheetIndex: 0,
        sheets: [
          {
            name: "Sheet1",
            rows: 3,
            cols: 3,
            contents: { A1: "=Sheet2!A1" },
          },
          {
            name: "Sheet2",
            rows: 3,
            cols: 3,
            contents: { A1: 42 },
          },
        ],
      },
      getSheetCellEditContent: (sheetIndex, address) => {
        const sheet = sheetIndex === 0
          ? { A1: "=Sheet2!A1" }
          : { A1: "42" };
        return String(sheet[address as keyof typeof sheet] ?? "");
      },
      getSheetCellValue: (sheetIndex, address) => {
        if (sheetIndex === 1 && address === "A1") return 42;
        return "";
      },
    });
    expect(blob).toBeInstanceOf(Blob);
    expect(blob.size).toBeGreaterThan(100);
  });
});
