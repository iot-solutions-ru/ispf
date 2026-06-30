import { describe, expect, it } from "vitest";
import { buildPackRecords } from "./build-pack.js";

describe("ISA symbol pack", () => {
  it("has unique ids and valid geometry", () => {
    const records = buildPackRecords();
    expect(records.length).toBeGreaterThan(50);
    const ids = new Set<string>();
    for (const rec of records) {
      expect(ids.has(rec.id)).toBe(false);
      ids.add(rec.id);
      expect(rec.svg).toContain("<g>");
      expect(rec.ports.length).toBeGreaterThan(0);
      expect(rec.viewBox).toMatch(/^0 0 \d+ \d+$/);
    }
  });
});
