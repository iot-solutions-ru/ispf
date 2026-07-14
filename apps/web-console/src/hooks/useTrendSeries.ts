import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { fetchVariableHistory, fetchVariableHistoryAggregate } from "../api";
import type { ChartSampleMode, WidgetHistoryRange } from "../types/dashboard";
import { useBoundVariable } from "./useBoundVariable";
import {
  historyRangeFrom,
  isCalendarHistoryRange,
  useVariableHistory,
  type HistoryRange,
} from "./useVariableHistory";
import { readFieldValue } from "../types/dashboard";
import { isPlottableTelemetryQuality, readRowQuality } from "../utils/telemetryQuality";
import {
  appendCoalescedTrendPoint,
  coalesceTrendPoints,
  DEFAULT_LIVE_COALESCE_MS,
  liveAggregateFromIso,
  resolveChartHistoryBucket,
  resolveEffectiveSampleMode,
} from "../utils/chartSampling";
import { useOptionalUserTimeZone } from "../context/UserTimeZoneContext";

export interface TrendPoint {
  t: number;
  time: string;
  value: number | null;
  quality?: string;
}

export interface TrendSeriesOptions {
  skipRangedHistory?: boolean;
  sampleMode?: ChartSampleMode;
  historyBucket?: string;
  liveCoalesceMs?: number;
}

function toNumeric(raw: unknown): number | null {
  if (typeof raw === "number" && Number.isFinite(raw)) {
    return raw;
  }
  if (typeof raw === "boolean") {
    return raw ? 1 : 0;
  }
  if (typeof raw === "string" && raw.trim() !== "") {
    const parsed = Number(raw);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

function sampleToPoint(ts: string, value: number | null, quality?: string): TrendPoint {
  const t = Date.parse(ts);
  return {
    t: Number.isFinite(t) ? t : Date.now(),
    time: new Date(Number.isFinite(t) ? t : Date.now()).toLocaleTimeString(),
    value,
    quality,
  };
}

function isHistoryRange(range: WidgetHistoryRange): range is HistoryRange {
  return range !== "live";
}

export function useTrendSeries(
  objectPath: string,
  variableName: string,
  valueField: string | undefined,
  refreshIntervalMs: number,
  maxPoints: number,
  historyRange: WidgetHistoryRange = "live",
  options: TrendSeriesOptions | boolean = {},
) {
  const opts: TrendSeriesOptions = typeof options === "boolean"
    ? { skipRangedHistory: options }
    : options;
  const field = valueField ?? "value";
  const coalesceMs = opts.liveCoalesceMs ?? DEFAULT_LIVE_COALESCE_MS;
  const boundQuery = useBoundVariable(objectPath, variableName, valueField, refreshIntervalMs);
  const row = boundQuery.variable?.value?.rows[0];
  const rawValue = readFieldValue(row, valueField);
  const [livePoints, setLivePoints] = useState<TrendPoint[]>([]);
  const historyKey = `${objectPath}|${variableName}|${field}|${maxPoints}|${historyRange}|${opts.sampleMode ?? "auto"}|${opts.historyBucket ?? "auto"}|${coalesceMs}`;
  const seededKeyRef = useRef<string | null>(null);
  const recordHistory = boundQuery.variable?.historyEnabled === true;
  const requestedMode = resolveEffectiveSampleMode(opts.sampleMode, recordHistory);
  // Coalesce applies to live tick appends; ranged windows use aggregate (or raw) instead.
  const effectiveMode =
    historyRange !== "live" && requestedMode === "coalesce"
      ? recordHistory
        ? "aggregate"
        : "raw"
      : requestedMode;
  const resolvedBucket = resolveChartHistoryBucket(historyRange, opts.historyBucket, maxPoints);
  const tz = useOptionalUserTimeZone();
  const useAggregate =
    effectiveMode === "aggregate"
    && recordHistory
    && !opts.skipRangedHistory
    && Boolean(objectPath && variableName);
  const useRangedHistory =
    historyRange !== "live"
    && recordHistory
    && !opts.skipRangedHistory
    && !useAggregate;
  const rangedHistory = useVariableHistory(objectPath, variableName, {
    field,
    range: isHistoryRange(historyRange) ? historyRange : "24h",
    limit: maxPoints,
    enabled: useRangedHistory && Boolean(objectPath && variableName),
    refreshIntervalMs,
    bucket: effectiveMode === "raw" ? null : resolvedBucket,
  });

  const calendarRange =
    isHistoryRange(historyRange) && isCalendarHistoryRange(historyRange) ? historyRange : undefined;

  const aggregateRefreshMs = Math.max(
    refreshIntervalMs || 5_000,
    Math.min(30_000, Math.max(5_000, Math.floor(bucketHintMs(resolvedBucket) / 2))),
  );

  const aggregateQuery = useQuery({
    queryKey: [
      "variable-history-aggregate-series",
      objectPath,
      variableName,
      field,
      historyRange,
      maxPoints,
      resolvedBucket,
      tz?.timeZone,
    ],
    queryFn: () => {
      const from =
        historyRange === "live"
          ? liveAggregateFromIso(resolvedBucket, maxPoints)
          : calendarRange
            ? undefined
            : historyRangeFrom(historyRange as HistoryRange);
      const to =
        calendarRange || (isHistoryRange(historyRange) && historyRange === "all")
          ? undefined
          : new Date().toISOString();
      return fetchVariableHistoryAggregate(objectPath, variableName, {
        field,
        bucket: resolvedBucket,
        from,
        to,
        calendarRange,
        timeZone: calendarRange ? tz?.timeZone ?? "UTC" : undefined,
        limit: maxPoints,
      });
    },
    enabled: useAggregate,
    staleTime: aggregateRefreshMs,
    refetchInterval: aggregateRefreshMs,
  });

  const livePollMs =
    effectiveMode === "raw"
      ? Math.max(1000, Math.min(refreshIntervalMs || 5000, 5_000))
      : Math.max(5_000, refreshIntervalMs || 15_000);
  const liveHistoryQuery = useQuery({
    queryKey: ["variable-history", objectPath, variableName, field, maxPoints, "live", effectiveMode],
    queryFn: () =>
      fetchVariableHistory(objectPath, variableName, {
        field,
        limit: Math.min(maxPoints * (effectiveMode === "coalesce" ? 4 : 1), 2_000),
      }),
    enabled: Boolean(
      objectPath
      && variableName
      && recordHistory
      && historyRange === "live"
      && !useAggregate
      && (effectiveMode === "raw" || effectiveMode === "coalesce"),
    ),
    staleTime: livePollMs,
    refetchInterval: livePollMs,
  });

  useEffect(() => {
    seededKeyRef.current = null;
    setLivePoints([]);
  }, [historyKey]);

  useEffect(() => {
    if (historyRange !== "live" || useAggregate) {
      return;
    }
    if (!liveHistoryQuery.data?.samples?.length) {
      return;
    }
    let seeded = liveHistoryQuery.data.samples
      .filter((sample) => sample.value != null && Number.isFinite(sample.value))
      .map((sample) => sampleToPoint(sample.ts, sample.value as number));
    if (effectiveMode === "coalesce") {
      seeded = coalesceTrendPoints(seeded, coalesceMs);
    }
    const next = seeded.length > maxPoints ? seeded.slice(-maxPoints) : seeded;
    seededKeyRef.current = historyKey;
    setLivePoints(next);
  }, [
    liveHistoryQuery.dataUpdatedAt,
    historyKey,
    maxPoints,
    historyRange,
    useAggregate,
    effectiveMode,
    coalesceMs,
  ]);

  useEffect(() => {
    if (historyRange !== "live" || useAggregate) {
      return;
    }
    const numeric = toNumeric(rawValue);
    const quality = readRowQuality(row as Record<string, unknown> | undefined);
    const now = Date.now();

    if (numeric === null && (quality === undefined || isPlottableTelemetryQuality(quality))) {
      return;
    }

    const plottable = numeric !== null && isPlottableTelemetryQuality(quality);
    const next: TrendPoint = {
      t: now,
      time: new Date(now).toLocaleTimeString(),
      value: plottable ? numeric : null,
      quality,
    };

    setLivePoints((prev) => {
      if (effectiveMode === "coalesce") {
        return appendCoalescedTrendPoint(prev, next, coalesceMs, maxPoints);
      }
      const last = prev[prev.length - 1];
      if (last && now - last.t < Math.max(400, refreshIntervalMs / 2)) {
        return prev;
      }
      const merged = [...prev, next];
      return merged.length > maxPoints ? merged.slice(-maxPoints) : merged;
    });
  }, [rawValue, row, maxPoints, refreshIntervalMs, historyRange, useAggregate, effectiveMode, coalesceMs]);

  const aggregatePoints = useMemo(() => {
    if (!aggregateQuery.data?.buckets?.length) {
      return [] as TrendPoint[];
    }
    return aggregateQuery.data.buckets
      .filter((item) => item.avg != null && Number.isFinite(item.avg))
      .map((item) => sampleToPoint(item.ts, item.avg as number));
  }, [aggregateQuery.data]);

  const liveStats = useMemo(() => {
    const source = useAggregate ? aggregatePoints : livePoints;
    const values = source
      .map((point) => point.value)
      .filter((value): value is number => value != null && Number.isFinite(value));
    if (values.length === 0) {
      return { min: null, max: null, latest: null };
    }
    return {
      min: Math.min(...values),
      max: Math.max(...values),
      latest: values[values.length - 1],
    };
  }, [livePoints, aggregatePoints, useAggregate]);

  if (useRangedHistory) {
    return {
      ...boundQuery,
      points: rangedHistory.points,
      stats: rangedHistory.stats,
      historyLoading: rangedHistory.isLoading,
      historyEnabled: recordHistory,
      historyRange,
      aggregated: rangedHistory.aggregated,
      historyBucket: rangedHistory.bucket,
      sampleMode: effectiveMode,
    };
  }

  if (useAggregate) {
    return {
      ...boundQuery,
      points: aggregatePoints,
      stats: liveStats,
      historyLoading: aggregateQuery.isLoading,
      historyEnabled: recordHistory,
      historyRange,
      aggregated: true,
      historyBucket: resolvedBucket,
      sampleMode: effectiveMode,
    };
  }

  return {
    ...boundQuery,
    points: livePoints,
    stats: liveStats,
    historyLoading: recordHistory && liveHistoryQuery.isLoading,
    historyEnabled: recordHistory,
    historyRange,
    aggregated: false,
    historyBucket: null as string | null,
    sampleMode: effectiveMode,
  };
}

function bucketHintMs(bucket: string): number {
  const normalized = bucket.trim().toLowerCase();
  switch (normalized) {
    case "1m":
      return 60_000;
    case "5m":
      return 5 * 60_000;
    case "15m":
      return 15 * 60_000;
    case "30m":
      return 30 * 60_000;
    case "1h":
      return 60 * 60_000;
    case "6h":
      return 6 * 60 * 60_000;
    case "1d":
      return 24 * 60 * 60_000;
    default:
      return 60_000;
  }
}
