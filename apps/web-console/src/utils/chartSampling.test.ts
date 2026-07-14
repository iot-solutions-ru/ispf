import { describe, expect, it } from "vitest";
import {
  appendCoalescedTrendPoint,
  coalesceTrendPoints,
  resolveChartHistoryBucket,
  resolveEffectiveSampleMode,
} from "./chartSampling";

describe("chartSampling", () => {
  it("defaults auto to aggregate when historized", () => {
    expect(resolveEffectiveSampleMode(undefined, true)).toBe("aggregate");
    expect(resolveEffectiveSampleMode("auto", false)).toBe("coalesce");
    expect(resolveEffectiveSampleMode("aggregate", false)).toBe("coalesce");
    expect(resolveEffectiveSampleMode("raw", true)).toBe("raw");
  });

  it("picks live/range historian buckets", () => {
    expect(resolveChartHistoryBucket("live", undefined, 120)).toBe("1m");
    expect(resolveChartHistoryBucket("live", undefined, 400)).toBe("5m");
    expect(resolveChartHistoryBucket("6h", undefined, 120)).toBe("15m");
    expect(resolveChartHistoryBucket("live", "5m", 120)).toBe("5m");
  });

  it("coalesces dense ticks into last-value slots", () => {
    const points = [
      { t: 1000, time: "a", value: 1 },
      { t: 1200, time: "b", value: 2 },
      { t: 2500, time: "c", value: 3 },
    ];
    expect(coalesceTrendPoints(points, 1000)).toEqual([
      { t: 1200, time: "b", value: 2 },
      { t: 2500, time: "c", value: 3 },
    ]);
  });

  it("appends with coalesce replacement inside the window", () => {
    const first = appendCoalescedTrendPoint([], { t: 1000, time: "a", value: 1 }, 1000, 10);
    const second = appendCoalescedTrendPoint(
      first,
      { t: 1500, time: "b", value: 2 },
      1000,
      10,
    );
    expect(second).toEqual([{ t: 1000, time: "a", value: 2 }]);
    const third = appendCoalescedTrendPoint(
      second,
      { t: 2200, time: "c", value: 3 },
      1000,
      10,
    );
    expect(third).toEqual([
      { t: 1000, time: "a", value: 2 },
      { t: 2200, time: "c", value: 3 },
    ]);
  });
});
