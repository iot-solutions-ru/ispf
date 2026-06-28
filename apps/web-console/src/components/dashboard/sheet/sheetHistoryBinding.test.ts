import { describe, expect, it } from "vitest";
import {
  bindingCellToHistoryRef,
  mergeSheetHistoryRefs,
  pickLatestHistorySample,
  resolveBindingHistoryMinutes,
} from "./sheetHistoryBinding";

describe("sheetHistoryBinding", () => {
  it("resolves binding history minutes", () => {
    expect(resolveBindingHistoryMinutes({ kind: "binding", historyMinutes: 15 })).toBe(15);
    expect(resolveBindingHistoryMinutes({ kind: "binding", historyMinutes: 0 })).toBeUndefined();
    expect(resolveBindingHistoryMinutes({ kind: "binding" })).toBeUndefined();
  });

  it("merges formula and binding history refs without duplicates", () => {
    const binding = bindingCellToHistoryRef(
      "root.platform.devices.demo-sensor-01",
      "temperature",
      "value",
      5
    );
    const merged = mergeSheetHistoryRefs(
      [
        {
          objectPath: "root.platform.devices.demo-sensor-01",
          variableName: "temperature",
          field: "value",
          histMinutes: 5,
        },
        {
          objectPath: "root.platform.devices.demo-sensor-01",
          variableName: "humidity",
          field: "value",
          histMinutes: 10,
        },
      ],
      [binding]
    );
    expect(merged).toHaveLength(2);
  });

  it("picks latest numeric history sample", () => {
    const value = pickLatestHistorySample(
      [
        { ts: "2026-01-01T10:00:00Z", value: 10, text: null },
        { ts: "2026-01-01T10:05:00Z", value: 12.5, text: null },
      ],
      "value"
    );
    expect(value).toBe(12.5);
  });
});
