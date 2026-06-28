import { describe, expect, it } from "vitest";
import {
  bucketsToCandlestickPoints,
  candlestickStats,
  livePointsToCandlestickPoints,
} from "./chartOhlcUtils";
import type { TrendPoint } from "../hooks/useTrendSeries";

function trendPoint(t: number, value: number): TrendPoint {
  return { t, time: new Date(t).toLocaleTimeString(), value };
}

describe("chartOhlcUtils", () => {
  it("livePointsToCandlestickPoints builds OHLC per chunk", () => {
    const points = [10, 12, 11, 15, 14, 16, 13].map((value, index) =>
      trendPoint(index * 60_000, value)
    );
    const candles = livePointsToCandlestickPoints(points, 3);
    expect(candles).toHaveLength(3);
    expect(candles[0]).toMatchObject({ open: 10, close: 11, high: 12, low: 10 });
    expect(candles[1]).toMatchObject({ open: 15, close: 16, high: 16, low: 14 });
  });

  it("livePointsToCandlestickPoints handles a single point", () => {
    const candles = livePointsToCandlestickPoints([trendPoint(0, 42)]);
    expect(candles).toEqual([
      expect.objectContaining({ open: 42, high: 42, low: 42, close: 42 }),
    ]);
  });

  it("bucketsToCandlestickPoints chains open from previous close", () => {
    const candles = bucketsToCandlestickPoints([
      { ts: "2026-01-01T10:00:00.000Z", avg: 20, min: 18, max: 22, count: 5 },
      { ts: "2026-01-01T11:00:00.000Z", avg: 24, min: 21, max: 26, count: 5 },
    ]);
    expect(candles).toHaveLength(2);
    expect(candles[0]).toMatchObject({ open: 20, close: 20, low: 18, high: 22 });
    expect(candles[1]).toMatchObject({ open: 20, close: 24, low: 21, high: 26 });
  });

  it("livePointsToCandlestickPoints returns empty array for no points", () => {
    expect(livePointsToCandlestickPoints([])).toEqual([]);
  });

  it("bucketsToCandlestickPoints skips buckets with missing aggregates", () => {
    const candles = bucketsToCandlestickPoints([
      { ts: "2026-01-01T10:00:00.000Z", avg: 20, min: 18, max: 22, count: 5 },
      { ts: "2026-01-01T11:00:00.000Z", avg: null, min: 21, max: 26, count: 5 },
    ]);
    expect(candles).toHaveLength(1);
    expect(candles[0]).toMatchObject({ close: 20 });
  });

  it("candlestickStats returns nulls for empty input", () => {
    expect(candlestickStats([])).toEqual({ min: null, max: null, latest: null });
  });

  it("candlestickStats returns low/high/close summary", () => {
    const stats = candlestickStats([
      {
        t: 0,
        time: "00:00",
        open: 10,
        high: 15,
        low: 9,
        close: 12,
      },
      {
        t: 60_000,
        time: "01:00",
        open: 12,
        high: 18,
        low: 11,
        close: 17,
      },
    ]);
    expect(stats).toEqual({ min: 9, max: 18, latest: 17 });
  });
});
