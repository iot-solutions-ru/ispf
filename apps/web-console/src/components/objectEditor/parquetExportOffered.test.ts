import { describe, expect, it } from "vitest";
import { parquetExportOffered } from "./VariableHistoryPanel";

describe("parquetExportOffered", () => {
  it("hides parquet unless this server reports the module", () => {
    expect(parquetExportOffered(undefined)).toBe(false);
    expect(parquetExportOffered({})).toBe(false);
    expect(parquetExportOffered({ parquetExportAvailable: false })).toBe(false);
    expect(parquetExportOffered({ parquetExportAvailable: true })).toBe(true);
  });
});
