import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { fetchVariableHistory } from "../api";
import { useBoundVariable } from "./useBoundVariable";
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

export function useTrendSeries(
  objectPath: string,
  variableName: string,
  valueField: string | undefined,
  refreshIntervalMs: number,
  maxPoints: number,
  historyEnabled = false
) {
  const field = valueField ?? "value";
  const query = useBoundVariable(objectPath, variableName, valueField, refreshIntervalMs);
  const row = query.variable?.value?.rows[0];
  const rawValue = readFieldValue(row, valueField);
  const [livePoints, setLivePoints] = useState<TrendPoint[]>([]);
  const historyKey = `${objectPath}|${variableName}|${field}|${maxPoints}`;
  const seededKeyRef = useRef<string | null>(null);
  const recordHistory = historyEnabled || query.variable?.historyEnabled === true;

  const historyQuery = useQuery({
    queryKey: ["variable-history", objectPath, variableName, field, maxPoints],
    queryFn: () =>
      fetchVariableHistory(objectPath, variableName, {
        field,
        limit: maxPoints,
      }),
    enabled: Boolean(objectPath && variableName && recordHistory),
    staleTime: refreshIntervalMs,
  });

  useEffect(() => {
    seededKeyRef.current = null;
    setLivePoints([]);
  }, [historyKey]);

  useEffect(() => {
    if (!historyQuery.data?.samples?.length) {
      return;
    }
    if (seededKeyRef.current === historyKey) {
      return;
    }
    seededKeyRef.current = historyKey;
    const seeded = historyQuery.data.samples
      .filter((sample) => sample.value != null && Number.isFinite(sample.value))
      .map((sample) => sampleToPoint(sample.ts, sample.value as number));
    setLivePoints(seeded.length > maxPoints ? seeded.slice(-maxPoints) : seeded);
  }, [historyQuery.data, historyKey, maxPoints]);

  useEffect(() => {
    const numeric = toNumeric(rawValue);
    if (numeric === null) return;

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
  }, [rawValue, maxPoints, refreshIntervalMs]);

  const points = livePoints;

  const stats = useMemo(() => {
    if (points.length === 0) {
      return { min: null, max: null, latest: null };
    }
    const values = points.map((point) => point.value);
    return {
      min: Math.min(...values),
      max: Math.max(...values),
      latest: values[values.length - 1],
    };
  }, [points]);

  return {
    ...query,
    points,
    stats,
    historyLoading: recordHistory && historyQuery.isLoading,
    historyEnabled: recordHistory,
  };
}
