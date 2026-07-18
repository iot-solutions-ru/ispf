import { describe, expect, it } from "vitest";
import {
  formatAuditValue,
  hasObjectAuditDiff,
  parseObjectAuditSummary,
} from "./objectAuditSummary";

describe("parseObjectAuditSummary", () => {
  it("parses before/after JSON", () => {
    const diff = parseObjectAuditSummary('{"before":"a","after":"b"}');
    expect(diff).toEqual({ before: "a", after: "b" });
    expect(hasObjectAuditDiff(diff)).toBe(true);
  });

  it("returns null for empty summary", () => {
    expect(parseObjectAuditSummary("")).toBeNull();
    expect(hasObjectAuditDiff(null)).toBe(false);
  });

  it("wraps legacy plain values as after", () => {
    const diff = parseObjectAuditSummary("true");
    expect(diff).toEqual({ before: null, after: true });
  });
});

describe("formatAuditValue", () => {
  it("pretty-prints objects", () => {
    expect(formatAuditValue({ x: 1 })).toBe('{\n  "x": 1\n}');
  });

  it("returns empty string for null", () => {
    expect(formatAuditValue(null)).toBe("");
  });
});
