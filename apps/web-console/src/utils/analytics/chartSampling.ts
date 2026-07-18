import type { ChartSampleMode, WidgetHistoryRange } from "../../types/dashboard";
import {
  historyBucketForRangeChart,
  type HistoryRange,
} from "../../hooks/useVariableHistory";

export type { ChartSampleMode };

export interface SampledTrendPoint {
  t: number;
  time: string;
  value: number | null;
  quality?: string;
}

export type ChartHistoryBucketId =
  | "auto"
  | "1m"
  | "5m"
  | "15m"
  | "30m"
  | "1h"
  | "6h"
  | "1d";

export const CHART_SAMPLE_MODE_IDS: ChartSampleMode[] = ["auto", "aggregate", "coalesce", "raw"];

export const CHART_HISTORY_BUCKET_OPTIONS: { id: ChartHistoryBucketId; label: string }[] = [
  { id: "auto", label: "auto" },
  { id: "1m", label: "1m" },
  { id: "5m", label: "5m" },
  { id: "15m", label: "15m" },
  { id: "30m", label: "30m" },
  { id: "1h", label: "1h" },
  { id: "6h", label: "6h" },
  { id: "1d", label: "1d" },
];

export const DEFAULT_LIVE_COALESCE_MS = 1_000;

const BUCKET_MS: Record<Exclude<ChartHistoryBucketId, "auto">, number> = {
  "1m": 60_000,
  "5m": 5 * 60_000,
  "15m": 15 * 60_000,
  "30m": 30 * 60_000,
  "1h": 60 * 60_000,
  "6h": 6 * 60 * 60_000,
  "1d": 24 * 60 * 60_000,
};

function isHistoryRange(range: WidgetHistoryRange): range is HistoryRange {
  return range !== "live";
}

/** Resolve auto → aggregate (historized) or coalesce (RAM-only live ticks). */
export function resolveEffectiveSampleMode(
  mode: ChartSampleMode | undefined,
  historyEnabled: boolean,
): "aggregate" | "coalesce" | "raw" {
  const requested = mode ?? "auto";
  if (requested === "auto") {
    return historyEnabled ? "aggregate" : "coalesce";
  }
  if (requested === "aggregate" && !historyEnabled) {
    return "coalesce";
  }
  return requested;
}

export function bucketDurationMs(bucket: string): number {
  const normalized = bucket.trim().toLowerCase() as Exclude<ChartHistoryBucketId, "auto">;
  return BUCKET_MS[normalized] ?? 60_000;
}

/**
 * Historian bucket for chart windows. Prefer explicit override; otherwise match range
 * (live defaults to 1m so maxPoints≈minutes of coverage).
 */
export function resolveChartHistoryBucket(
  historyRange: WidgetHistoryRange,
  bucketOverride: string | undefined,
  maxPoints: number,
): string {
  const override = bucketOverride?.trim().toLowerCase();
  if (override && override !== "auto") {
    return override;
  }
  if (historyRange === "live") {
    return maxPoints > 360 ? "5m" : "1m";
  }
  if (isHistoryRange(historyRange)) {
    return historyBucketForRangeChart(historyRange);
  }
  return "5m";
}

/** Sliding live aggregate window: about `maxPoints` buckets ending now. */
export function liveAggregateFromIso(bucket: string, maxPoints: number): string {
  const spanMs = bucketDurationMs(bucket) * Math.max(10, maxPoints);
  return new Date(Date.now() - spanMs).toISOString();
}

/** Last-value-wins downsample into fixed time slots (client coalesce). */
export function coalesceTrendPoints(
  points: SampledTrendPoint[],
  coalesceMs: number,
): SampledTrendPoint[] {
  const windowMs = Math.max(200, coalesceMs);
  if (points.length <= 1) {
    return points;
  }
  const bySlot = new Map<number, SampledTrendPoint>();
  for (const point of points) {
    const slot = Math.floor(point.t / windowMs);
    bySlot.set(slot, point);
  }
  return [...bySlot.entries()]
    .sort((left, right) => left[0] - right[0])
    .map((entry) => entry[1]);
}

/** Append one live sample into a coalesced series (mutates via return). */
export function appendCoalescedTrendPoint(
  previous: SampledTrendPoint[],
  next: SampledTrendPoint,
  coalesceMs: number,
  maxPoints: number,
): SampledTrendPoint[] {
  const windowMs = Math.max(200, coalesceMs);
  const last = previous[previous.length - 1];
  if (last && next.t - last.t < windowMs) {
    const replaced = previous.slice(0, -1);
    replaced.push({ ...next, t: last.t, time: last.time });
    return replaced.length > maxPoints ? replaced.slice(-maxPoints) : replaced;
  }
  const merged = [...previous, next];
  return merged.length > maxPoints ? merged.slice(-maxPoints) : merged;
}
