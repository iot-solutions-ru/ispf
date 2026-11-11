import { describe, expect, it } from "vitest";
import { defaultTrendAxis, normalizedPenKey, withNormalizedPens } from "./trendScale";

describe("trend scale", () => {
  it("puts even pens on the left axis and odd pens on the right", () => {
    expect(defaultTrendAxis(0)).toBe("left");
    expect(defaultTrendAxis(1)).toBe("right");
    expect(defaultTrendAxis(2)).toBe("left");
  });

  it("scales each pen to its own range and keeps the raw value", () => {
    const points = withNormalizedPens(
      [
        { t: 1, time: "a", pressure: 0, flow: 1000 },
        { t: 2, time: "b", pressure: 6, flow: 5000 },
      ],
      ["pressure", "flow"],
    );
    expect(points[0].pressure).toBe(0);
    expect(points[1].pressure).toBe(6);
    expect(points[0][normalizedPenKey("pressure")]).toBe(0);
    expect(points[1][normalizedPenKey("pressure")]).toBe(100);
    expect(points[0][normalizedPenKey("flow")]).toBe(0);
    expect(points[1][normalizedPenKey("flow")]).toBe(100);
  });

  it("leaves a missing sample unset so the line can skip it", () => {
    const points = withNormalizedPens(
      [
        { t: 1, time: "a", pressure: 0 },
        { t: 2, time: "b", pressure: 6, flow: 5000 },
      ],
      ["pressure", "flow"],
    );
    expect(points[0][normalizedPenKey("flow")]).toBeUndefined();
    expect(points[1][normalizedPenKey("flow")]).toBe(50);
  });

  it("places a flat series at the middle of the normalized axis", () => {
    const points = withNormalizedPens(
      [
        { t: 1, time: "a", level: 4 },
        { t: 2, time: "b", level: 4 },
      ],
      ["level"],
    );
    expect(points[0][normalizedPenKey("level")]).toBe(50);
    expect(points[1][normalizedPenKey("level")]).toBe(50);
  });
});