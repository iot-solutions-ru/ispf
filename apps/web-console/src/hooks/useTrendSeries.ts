import { useEffect, useMemo, useState } from "react";
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
  if (typeof raw === "string" && raw.trim() !== "") {
    const parsed = Number(raw);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

export function useTrendSeries(
  objectPath: string,
  variableName: string,
  valueField: string | undefined,
  refreshIntervalMs: number,
  maxPoints: number
) {
  const query = useBoundVariable(objectPath, variableName, valueField, refreshIntervalMs);
  const row = query.variable?.value?.rows[0];
  const rawValue = readFieldValue(row, valueField);
  const [points, setPoints] = useState<TrendPoint[]>([]);

  useEffect(() => {
    const numeric = toNumeric(rawValue);
    if (numeric === null) return;

    const now = Date.now();
    setPoints((prev) => {
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

  return { ...query, points, stats };
}
