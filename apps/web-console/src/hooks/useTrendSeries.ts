import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { fetchVariableHistory } from "../api";
import type { WidgetHistoryRange } from "../types/dashboard";
import { useBoundVariable } from "./useBoundVariable";
import { useVariableHistory, type HistoryRange } from "./useVariableHistory";
import { readFieldValue } from "../types/dashboard";

export interface TrendPoint {
  t: number;
  time: string;
  value: number;
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

function sampleToPoint(ts: string, value: number): TrendPoint {
  const t = Date.parse(ts);
  return {
    t: Number.isFinite(t) ? t : Date.now(),
    time: new Date(Number.isFinite(t) ? t : Date.now()).toLocaleTimeString(),
    value,
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
  historyRange: WidgetHistoryRange = "live"
) {
  const field = valueField ?? "value";
  const boundQuery = useBoundVariable(objectPath, variableName, valueField, refreshIntervalMs);
  const row = boundQuery.variable?.value?.rows[0];
  const rawValue = readFieldValue(row, valueField);
  const [livePoints, setLivePoints] = useState<TrendPoint[]>([]);
  const historyKey = `${objectPath}|${variableName}|${field}|${maxPoints}|${historyRange}`;
  const seededKeyRef = useRef<string | null>(null);
  const recordHistory = boundQuery.variable?.historyEnabled === true;
  const useRangedHistory = historyRange !== "live" && recordHistory;

  const rangedHistory = useVariableHistory(objectPath, variableName, {
    field,
    range: isHistoryRange(historyRange) ? historyRange : "24h",
    limit: maxPoints,
    enabled: useRangedHistory && Boolean(objectPath && variableName),
    refreshIntervalMs,
  });

  const liveHistoryQuery = useQuery({
    queryKey: ["variable-history", objectPath, variableName, field, maxPoints, "live"],
    queryFn: () =>
      fetchVariableHistory(objectPath, variableName, {
        field,
        limit: maxPoints,
      }),
    enabled: Boolean(objectPath && variableName && recordHistory && historyRange === "live"),
    staleTime: refreshIntervalMs,
  });

  useEffect(() => {
    seededKeyRef.current = null;
    setLivePoints([]);
  }, [historyKey]);

  useEffect(() => {
    if (historyRange !== "live") {
      return;
    }
    if (!liveHistoryQuery.data?.samples?.length) {
      return;
    }
    if (seededKeyRef.current === historyKey) {
      return;
    }
    seededKeyRef.current = historyKey;
    const seeded = liveHistoryQuery.data.samples
      .filter((sample) => sample.value != null && Number.isFinite(sample.value))
      .map((sample) => sampleToPoint(sample.ts, sample.value as number));
    setLivePoints(seeded.length > maxPoints ? seeded.slice(-maxPoints) : seeded);
  }, [liveHistoryQuery.data, historyKey, maxPoints, historyRange]);

  useEffect(() => {
    if (historyRange !== "live") {
      return;
    }
    const numeric = toNumeric(rawValue);
    if (numeric === null) {
      return;
    }

    const now = Date.now();
    setLivePoints((prev) => {
      const last = prev[prev.length - 1];
      if (last && now - last.t < Math.max(400, refreshIntervalMs / 2)) {
        return prev;
      }
      const next: TrendPoint = {
        t: now,
        time: new Date(now).toLocaleTimeString(),
        value: numeric,
      };
      const merged = [...prev, next];
      return merged.length > maxPoints ? merged.slice(-maxPoints) : merged;
    });
  }, [rawValue, maxPoints, refreshIntervalMs, historyRange]);

  const liveStats = useMemo(() => {
    if (livePoints.length === 0) {
      return { min: null, max: null, latest: null };
    }
    const values = livePoints.map((point) => point.value);
    return {
      min: Math.min(...values),
      max: Math.max(...values),
      latest: values[values.length - 1],
    };
  }, [livePoints]);

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
  };
}
