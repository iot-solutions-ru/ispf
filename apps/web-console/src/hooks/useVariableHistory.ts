import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  fetchVariableHistory,
  fetchVariableHistoryAggregate,
  type VariableHistoryAggregateResponse,
  type VariableHistoryResponse,
} from "../api";
import type { TrendPoint } from "./useTrendSeries";
import { useOptionalUserTimeZone } from "../context/UserTimeZoneContext";

export type HistoryRange = "1h" | "6h" | "24h" | "7d" | "today" | "yesterday" | "all";

export type CalendarHistoryRange = "today" | "yesterday";

type VariableHistoryQueryData = VariableHistoryResponse | VariableHistoryAggregateResponse;

function isAggregateResponse(
  data: VariableHistoryQueryData
): data is VariableHistoryAggregateResponse {
  return "buckets" in data;
}

export function isCalendarHistoryRange(range: HistoryRange): range is CalendarHistoryRange {
  return range === "today" || range === "yesterday";
}

const RANGE_MS: Record<Exclude<HistoryRange, "all" | CalendarHistoryRange>, number> = {
  "1h": 60 * 60 * 1000,
  "6h": 6 * 60 * 60 * 1000,
  "24h": 24 * 60 * 60 * 1000,
  "7d": 7 * 24 * 60 * 60 * 1000,
};

export function historyRangeFrom(range: HistoryRange): string | undefined {
  if (range === "all" || isCalendarHistoryRange(range)) {
    return undefined;
  }
  return new Date(Date.now() - RANGE_MS[range]).toISOString();
}

/** Server-side bucket for long ranges (avg/min/max per interval). */
export function historyBucketForRange(range: HistoryRange): string | null {
  if (range === "7d" || range === "yesterday") {
    return "1h";
  }
  if (range === "today") {
    return "15m";
  }
  if (range === "all") {
    return "6h";
  }
  return null;
}

/** Bucket sizes for chart min/max band (always uses aggregate API). */
export function historyBucketForRangeChart(range: HistoryRange): string {
  switch (range) {
    case "1h":
      return "5m";
    case "6h":
      return "15m";
    case "24h":
      return "30m";
    case "today":
      return "15m";
    case "yesterday":
      return "1h";
    case "7d":
      return "1h";
    case "all":
      return "6h";
  }
}

function sampleToPoint(ts: string, value: number, formatDate?: (v: string) => string): TrendPoint {
  const t = Date.parse(ts);
  const date = new Date(Number.isFinite(t) ? t : Date.now());
  return {
    t: date.getTime(),
    time: formatDate ? formatDate(ts) : date.toLocaleString(),
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
  const tz = useOptionalUserTimeZone();
  const formatDate = tz?.formatDate;
  const calendarRange = isCalendarHistoryRange(range) ? range : undefined;
  const from = calendarRange ? undefined : historyRangeFrom(range);
  const to = calendarRange || range === "all" ? undefined : new Date().toISOString();
  const bucket = historyBucketForRange(range);
  const aggregated = bucket != null;

  const query = useQuery<VariableHistoryQueryData>({
    queryKey: [
      "variable-history",
      objectPath,
      variableName,
      field,
      range,
      limit,
      bucket,
      tz?.timeZone,
    ],
    queryFn: (): Promise<VariableHistoryQueryData> => {
      const calendarOpts = calendarRange
        ? { calendarRange, timeZone: tz?.timeZone ?? "UTC" }
        : {};
      if (aggregated && bucket) {
        return fetchVariableHistoryAggregate(objectPath, variableName, {
          field,
          bucket,
          from,
          to,
          limit,
          ...calendarOpts,
        });
      }
      return fetchVariableHistory(objectPath, variableName, {
        field,
        from,
        to,
        limit,
        ...calendarOpts,
      });
    },
    enabled,
    staleTime: options?.refreshIntervalMs ?? 30_000,
    refetchInterval: options?.refreshIntervalMs,
  });

  const points = useMemo(() => {
    if (!query.data) {
      return [] as TrendPoint[];
    }
    if (isAggregateResponse(query.data) && query.data.buckets.length > 0) {
      return query.data.buckets
        .filter((item) => item.avg != null && Number.isFinite(item.avg))
        .map((item) => sampleToPoint(item.ts, item.avg as number, formatDate));
    }
    if (!isAggregateResponse(query.data) && query.data.samples.length > 0) {
      return query.data.samples
        .filter((sample) => sample.value != null && Number.isFinite(sample.value))
        .map((sample) => sampleToPoint(sample.ts, sample.value as number, formatDate));
    }
    return [] as TrendPoint[];
  }, [query.data, formatDate]);

  const stats = useMemo(() => {
    if (!query.data) {
      return { min: null, max: null, latest: null };
    }
    if (isAggregateResponse(query.data) && query.data.buckets.length > 0) {
      const mins = query.data.buckets
        .map((item) => item.min)
        .filter((value): value is number => value != null && Number.isFinite(value));
      const maxs = query.data.buckets
        .map((item) => item.max)
        .filter((value): value is number => value != null && Number.isFinite(value));
      const avgs = query.data.buckets
        .map((item) => item.avg)
        .filter((value): value is number => value != null && Number.isFinite(value));
      if (avgs.length === 0) {
        return { min: null, max: null, latest: null };
      }
      return {
        min: mins.length ? Math.min(...mins) : null,
        max: maxs.length ? Math.max(...maxs) : null,
        latest: avgs[avgs.length - 1],
      };
    }
    if (points.length === 0) {
      return { min: null, max: null, latest: null };
    }
    const values = points.map((point) => point.value);
    return {
      min: Math.min(...values),
      max: Math.max(...values),
      latest: values[values.length - 1],
    };
  }, [points, query.data]);

  return {
    ...query,
    points,
    stats,
    field,
    range,
    aggregated,
    bucket,
  };
}
