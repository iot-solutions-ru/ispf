import { describe, expect, it } from "vitest";
import {
  layoutGridMaxW,
  layoutGridMaxX,
  parseLayoutGridInt,
  parseOptionalZIndex,
} from "./widgetLayoutFieldParse";

describe("widgetLayoutFieldParse", () => {
  it("parseLayoutGridInt ignores empty and invalid", () => {
    expect(parseLayoutGridInt("", 0, 11)).toBeUndefined();
    expect(parseLayoutGridInt("  ", 0)).toBeUndefined();
    expect(parseLayoutGridInt("abc", 0)).toBeUndefined();
  });

  it("parseLayoutGridInt clamps to min and max", () => {
    expect(parseLayoutGridInt("3", 0, 11)).toBe(3);
    expect(parseLayoutGridInt("-1", 0, 11)).toBe(0);
    expect(parseLayoutGridInt("99", 1, 12)).toBe(12);
  });

  it("layoutGridMaxX/W match fine grid (84 cols, demo sparkline slot)", () => {
    expect(layoutGridMaxX(84, 28)).toBe(56);
    expect(layoutGridMaxW(84, 56)).toBe(28);
  });

  it("parseOptionalZIndex clears or parses", () => {
    expect(parseOptionalZIndex("")).toBeUndefined();
    expect(parseOptionalZIndex("2.9")).toBe(2);
    expect(parseOptionalZIndex("n/a")).toBeNull();
  });
});
