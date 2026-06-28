import { describe, expect, it } from "vitest";
import {
  dateToExcelSerial,
  excelDate,
  excelDay,
  excelMonth,
  excelRandBetween,
  excelTodaySerial,
  excelYear,
} from "./sheetDateFunctions";

describe("sheetDateFunctions", () => {
  it("converts DATE parts to Excel serial", () => {
    expect(excelDate(2027, 1, 1)).toBeGreaterThan(excelTodaySerial());
    expect(excelYear(excelDate(2027, 1, 1))).toBe(2027);
    expect(excelMonth(excelDate(2027, 1, 1))).toBe(1);
    expect(excelDay(excelDate(2027, 1, 1))).toBe(1);
  });

  it("returns local calendar year/month for today serial", () => {
    const now = new Date();
    expect(excelYear(excelTodaySerial())).toBe(now.getFullYear());
    expect(excelMonth(excelTodaySerial())).toBe(now.getMonth() + 1);
    expect(excelDay(excelTodaySerial())).toBe(now.getDate());
  });

  it("round-trips calendar dates through serial encoding", () => {
    const serial = dateToExcelSerial(new Date(2024, 5, 15));
    expect(excelYear(serial)).toBe(2024);
    expect(excelMonth(serial)).toBe(6);
    expect(excelDay(serial)).toBe(15);
  });

  it("generates RANDBETWEEN within bounds", () => {
    for (let i = 0; i < 20; i++) {
      const value = excelRandBetween(1, 100);
      expect(value).toBeGreaterThanOrEqual(1);
      expect(value).toBeLessThanOrEqual(100);
    }
  });
});
