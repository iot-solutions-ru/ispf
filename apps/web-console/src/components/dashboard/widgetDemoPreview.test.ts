import { describe, expect, it } from "vitest";
import {
  buildDemoCandlestickPoints,
  buildDemoRangeTrendPoints,
  buildDemoTrendPoints,
  parseDemoPreview,
} from "./widgetDemoPreview";

describe("parseDemoPreview", () => {
  it("returns null for missing or invalid JSON", () => {
    expect(parseDemoPreview(undefined)).toBeNull();
    expect(parseDemoPreview("{not-json")).toBeNull();
  });

  it("parses chart demo payload", () => {
    const parsed = parseDemoPreview<Array<{ t?: number; v: number }>>('[{"v":12},{"v":18}]');
    expect(parsed).toEqual([{ v: 12 }, { v: 18 }]);
  });
});

describe("buildDemoTrendPoints", () => {
  it("builds time/value points with synthetic timestamps", () => {
    const points = buildDemoTrendPoints([{ v: 10 }, { v: 20 }, { v: 15 }]);
    expect(points).toHaveLength(3);
    expect(points[0]?.value).toBe(10);
    expect(points[2]?.value).toBe(15);
    expect(points[0]?.time).toMatch(/\d/);
    expect(points[0]?.t).toBeLessThan(points[1]?.t ?? 0);
  });

  it("preserves explicit timestamps", () => {
    const points = buildDemoTrendPoints([
      { t: 1_700_000_000_000, v: 7 },
      { t: 1_700_000_060_000, v: 9 },
    ]);
    expect(points[0]?.t).toBe(1_700_000_000_000);
    expect(points[1]?.t).toBe(1_700_000_060_000);
  });
});

describe("buildDemoRangeTrendPoints", () => {
  it("derives min/max band around demo values", () => {
    const rangePoints = buildDemoRangeTrendPoints([{ v: 20 }, { v: 24 }, { v: 22 }]);
    expect(rangePoints).toHaveLength(3);
    for (const point of rangePoints) {
      expect(point.min).toBeLessThan(point.avg);
      expect(point.max).toBeGreaterThan(point.avg);
      expect(point.band).toBeGreaterThan(0);
    }
  });

  it("returns empty array when demo payload is missing", () => {
    expect(buildDemoRangeTrendPoints(null)).toEqual([]);
    expect(buildDemoRangeTrendPoints([])).toEqual([]);
  });
});

describe("buildDemoCandlestickPoints", () => {
  it("derives OHLC around demo values", () => {
    const candles = buildDemoCandlestickPoints([{ v: 20 }, { v: 24 }, { v: 22 }]);
    expect(candles).toHaveLength(3);
    for (const point of candles) {
      expect(point.high).toBeGreaterThanOrEqual(Math.max(point.open, point.close));
      expect(point.low).toBeLessThanOrEqual(Math.min(point.open, point.close));
    }
  });

  it("returns empty array when demo payload is missing", () => {
    expect(buildDemoCandlestickPoints(null)).toEqual([]);
    expect(buildDemoCandlestickPoints([])).toEqual([]);
  });
});
