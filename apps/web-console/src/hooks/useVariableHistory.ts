import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { fetchVariableHistory } from "../api";
import type { TrendPoint } from "./useTrendSeries";

export type HistoryRange = "1h" | "6h" | "24h" | "7d" | "all";

const RANGE_MS: Record<Exclude<HistoryRange, "all">, number> = {
  "1h": 60 * 60 * 1000,
  "6h": 6 * 60 * 60 * 1000,
  "24h": 24 * 60 * 60 * 1000,
  "7d": 7 * 24 * 60 * 60 * 1000,
};

export function historyRangeFrom(range: HistoryRange): string | undefined {
  if (range === "all") {
    return undefined;
  }
  return new Date(Date.now() - RANGE_MS[range]).toISOString();
}

function sampleToPoint(ts: string, value: number): TrendPoint {
  const t = Date.parse(ts);
  return {
    t: Number.isFinite(t) ? t : Date.now(),
    time: new Date(Number.isFinite(t) ? t : Date.now()).toLocaleString(),
    value,
  };
}

export function useVariableHistory(
  objectPath: string,
  variableName: string,
  options?: {
    field?: string;
    range?: HistoryRange;
    limit?: number;
    enabled?: boolean;
    refreshIntervalMs?: number;
  }
) {
  const field = options?.field ?? "value";
  const range = options?.range ?? "24h";
  const limit = options?.limit ?? 500;
  const enabled = options?.enabled !== false && Boolean(objectPath && variableName);
  const from = historyRangeFrom(range);

  const query = useQuery({
    queryKey: ["variable-history", objectPath, variableName, field, range, limit],
    queryFn: () =>
      fetchVariableHistory(objectPath, variableName, {
        field,
        from,
        to: new Date().toISOString(),
        limit,
      }),
    enabled,
    staleTime: options?.refreshIntervalMs ?? 30_000,
    refetchInterval: options?.refreshIntervalMs,
  });

  const points = useMemo(() => {
    if (!query.data?.samples?.length) {
      return [] as TrendPoint[];
    }
    return query.data.samples
      .filter((sample) => sample.value != null && Number.isFinite(sample.value))
      .map((sample) => sampleToPoint(sample.ts, sample.value as number));
  }, [query.data]);

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
    field,
    range,
  };
}
