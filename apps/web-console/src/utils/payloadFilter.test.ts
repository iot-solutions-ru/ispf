import { describe, expect, it } from "vitest";
import { matchesPayloadFilter } from "./payloadFilter";

describe("matchesPayloadFilter", () => {
  const row = { count: 12, name: "abcSensor", level: 5 };

  it("passes when expression is empty", () => {
    expect(matchesPayloadFilter(row, undefined)).toBe(true);
    expect(matchesPayloadFilter(row, "  ")).toBe(true);
  });

  it("evaluates numeric comparisons", () => {
    expect(matchesPayloadFilter(row, "count>10")).toBe(true);
    expect(matchesPayloadFilter(row, "count>=12")).toBe(true);
    expect(matchesPayloadFilter(row, "level<10")).toBe(true);
    expect(matchesPayloadFilter(row, "count<5")).toBe(false);
  });

  it("evaluates string contains", () => {
    expect(matchesPayloadFilter(row, "name contains abc")).toBe(true);
    expect(matchesPayloadFilter(row, "name contains xyz")).toBe(false);
  });

  it("combines conditions with &&", () => {
    expect(matchesPayloadFilter(row, "count>10 && name contains abc")).toBe(true);
    expect(matchesPayloadFilter(row, "count>10 && name contains xyz")).toBe(false);
  });

  it("combines conditions with ||", () => {
    expect(matchesPayloadFilter(row, "count>10 || name contains xyz")).toBe(true);
    expect(matchesPayloadFilter(row, "count<5 || name contains abc")).toBe(true);
    expect(matchesPayloadFilter(row, "count<5 || name contains xyz")).toBe(false);
  });
});
