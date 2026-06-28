import { describe, expect, it } from "vitest";
import fs from "node:fs";
import path from "node:path";
import { loadXlsxWorkbook, normalizeOoxmlPart } from "./sheetXlsxNormalize";

describe("sheetXlsxNormalize", () => {
  it("strips s: spreadsheetml prefix", () => {
    const input =
      '<s:worksheet xmlns:s="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><s:sheetData/></s:worksheet>';
    expect(normalizeOoxmlPart(input)).toBe(
      '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData/></worksheet>'
    );
  });

  it("loads Yandex Sheets export when present on disk", async () => {
    const sample = path.resolve(
      "c:/Users/micha/YandexDisk/ИОТ Решения/Аналитика_Свое_решение.xlsx"
    );
    if (!fs.existsSync(sample)) {
      return;
    }
    const ExcelJS = (await import("exceljs")).default;
    const workbook = await loadXlsxWorkbook(ExcelJS, fs.readFileSync(sample).buffer);
    expect(workbook.worksheets.length).toBe(6);
    expect(workbook.worksheets.map((ws) => ws.name)).toContain("BRD");
  });
});
