import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { fetchAnalyticsQuery, type AnalyticsQueryTagInput } from "../api";
import type { WidgetHistoryRange } from "../types/dashboard";
import {
  historyBucketForRangeChart,
  historyRangeFrom,
  isCalendarHistoryRange,
  type HistoryRange,
} from "./useVariableHistory";

const MULTI_SERIES_COLORS = ["#2f81f7", "#e67e22", "#27ae60", "#9b59b6", "#e74c3c", "#16a085"];
/** Multi-tag query is heavier than live binding; avoid dashboard poll hammering rate limiter. */
const MULTI_QUERY_MIN_REFRESH_MS = 30_000;

export interface AnalyticsMultiSeriesPoint {
  t: number;
  time: string;
  [seriesId: string]: number | string | null;
}

function isHistoryRange(range: WidgetHistoryRange): range is HistoryRange {
  return range !== "live";
}

export function parseAnalyticsQueryTags(json: string | undefined): AnalyticsQueryTagInput[] {
  if (!json?.trim()) {
    return [];
  }
  try {
    const parsed = JSON.parse(json) as AnalyticsQueryTagInput[];
    return Array.isArray(parsed) ? parsed.filter((tag) => tag.path && tag.variable) : [];
  } catch {
    return [];
  }
}

export function useAnalyticsMultiSeries(
  tagsJson: string | undefined,
  historyRange: WidgetHistoryRange,
  refreshIntervalMs: number,
  maxPoints: number,
  bucketOverride?: string | null,
) {
  const tags = useMemo(() => parseAnalyticsQueryTags(tagsJson), [tagsJson]);
  const refreshMs = Math.max(refreshIntervalMs, MULTI_QUERY_MIN_REFRESH_MS);
  const bucket =
    bucketOverride ??
    (historyRange !== "live" && isHistoryRange(historyRange)
      ? historyBucketForRangeChart(historyRange)
      : "1h");
  const calendarRange =
    isHistoryRange(historyRange) && isCalendarHistoryRange(historyRange) ? historyRange : undefined;
  const from =
    bucket && isHistoryRange(historyRange) && !calendarRange ? historyRangeFrom(historyRange) : undefined;
  const to = calendarRange || historyRange === "all" ? undefined : new Date().toISOString();

  const query = useQuery({
    queryKey: ["analytics-multi-query", tags, historyRange, bucket, maxPoints, from, to],
    queryFn: () =>
      fetchAnalyticsQuery({
        tags,
        bucket: bucket as string,
        from: from ?? new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString(),
        to: to ?? new Date().toISOString(),
        agg: "avg",
        maxBuckets: maxPoints,
      }),
    enabled: tags.length > 0 && Boolean(bucket),
    staleTime: refreshMs,
    refetchInterval: (query) => (query.state.error ? false : refreshMs),
    retry: false,
    placeholderData: (previous) => previous,
  });

  const points = useMemo(() => {
    if (!query.data?.timestamps?.length) {
      return [] as AnalyticsMultiSeriesPoint[];
    }
    return query.data.timestamps.map((timestamp, index) => {
      const t = Date.parse(timestamp);
      const row: AnalyticsMultiSeriesPoint = {
        t: Number.isFinite(t) ? t : Date.now(),
        time: new Date(Number.isFinite(t) ? t : Date.now()).toLocaleTimeString(),
      };
      for (const series of query.data.series) {
        row[series.id] = series.values[index] ?? null;
      }
      return row;
    });
  }, [query.data]);

  const seriesIds = query.data?.series.map((series) => series.id) ?? [];

  return {
    tags,
    points,
    seriesIds,
    colors: MULTI_SERIES_COLORS,
    bucket: query.data?.bucket ?? bucket,
    loading: query.isLoading,
    isError: query.isError,
    latencyMs: query.data?.latencyMs ?? 0,
  };
}
