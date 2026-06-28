import type { VariableHistoryBucket } from "../api";
import type { TrendPoint } from "../hooks/useTrendSeries";

export interface CandlestickPoint {
  t: number;
  time: string;
  open: number;
  high: number;
  low: number;
  close: number;
}

export function livePointsToCandlestickPoints(
  points: TrendPoint[],
  targetBuckets = 16
): CandlestickPoint[] {
  if (points.length === 0) {
    return [];
  }
  if (points.length === 1) {
    const point = points[0];
    return [{ ...point, open: point.value, high: point.value, low: point.value, close: point.value }];
  }
  const chunkSize = Math.max(1, Math.ceil(points.length / targetBuckets));
  const result: CandlestickPoint[] = [];
  for (let index = 0; index < points.length; index += chunkSize) {
    const chunk = points.slice(index, index + chunkSize);
    const values = chunk.map((point) => point.value);
    const anchor = chunk[chunk.length - 1];
    result.push({
      t: anchor.t,
      time: anchor.time,
      open: chunk[0].value,
      high: Math.max(...values),
      low: Math.min(...values),
      close: chunk[chunk.length - 1].value,
    });
  }
  return result;
}

/** Synthetic OHLC from historian aggregate buckets (open = prev close, close = avg). */
export function bucketsToCandlestickPoints(buckets: VariableHistoryBucket[]): CandlestickPoint[] {
  const valid = buckets.filter(
    (item) =>
      item.min != null &&
      item.max != null &&
      item.avg != null &&
      Number.isFinite(item.min) &&
      Number.isFinite(item.max) &&
      Number.isFinite(item.avg)
  );
  let previousClose: number | null = null;
  return valid.map((item) => {
    const t = Date.parse(item.ts);
    const low = item.min as number;
    const high = item.max as number;
    const close = item.avg as number;
    const open = previousClose ?? close;
    previousClose = close;
    return {
      t: Number.isFinite(t) ? t : Date.now(),
      time: new Date(Number.isFinite(t) ? t : Date.now()).toLocaleTimeString(),
      open,
      high,
      low,
      close,
    };
  });
}

export function candlestickStats(points: CandlestickPoint[]) {
  if (points.length === 0) {
    return { min: null, max: null, latest: null };
  }
  return {
    min: Math.min(...points.map((point) => point.low)),
    max: Math.max(...points.map((point) => point.high)),
    latest: points[points.length - 1].close,
  };
}
