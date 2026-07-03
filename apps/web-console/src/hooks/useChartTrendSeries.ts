import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { fetchVariableHistoryAggregate, type VariableHistoryBucket } from "../api";
import type { WidgetHistoryRange } from "../types/dashboard";
import {
  bucketsToCandlestickPoints,
  candlestickStats,
  livePointsToCandlestickPoints,
  type CandlestickPoint,
} from "../utils/chartOhlcUtils";
import {
  historyBucketForRangeChart,
  historyRangeFrom,
  isCalendarHistoryRange,
  type HistoryRange,
} from "./useVariableHistory";
import { useOptionalUserTimeZone } from "../context/UserTimeZoneContext";
import { useTrendSeries, type TrendPoint } from "./useTrendSeries";

export type ChartSeriesMode = "line" | "range" | "candlestick";

export interface RangeTrendPoint {
  t: number;
  time: string;
  min: number;
  max: number;
  avg: number;
  /** max − min for stacked area band */
  band: number;
}

export type { CandlestickPoint };

function isHistoryRange(range: WidgetHistoryRange): range is HistoryRange {
  return range !== "live";
}

function bucketsToRangePoints(buckets: VariableHistoryBucket[]): RangeTrendPoint[] {
  return buckets
    .filter(
      (item) =>
        item.min != null &&
        item.max != null &&
        item.avg != null &&
        Number.isFinite(item.min) &&
        Number.isFinite(item.max) &&
        Number.isFinite(item.avg)
    )
    .map((item) => {
      const t = Date.parse(item.ts);
      const min = item.min as number;
      const max = item.max as number;
      const avg = item.avg as number;
      return {
        t: Number.isFinite(t) ? t : Date.now(),
        time: new Date(Number.isFinite(t) ? t : Date.now()).toLocaleTimeString(),
        min,
        max,
        avg,
        band: Math.max(0, max - min),
      };
    });
}

function livePointsToRangePoints(points: TrendPoint[], targetBuckets = 16): RangeTrendPoint[] {
  const plottable = points.filter((point) => point.value != null && Number.isFinite(point.value));
  if (plottable.length === 0) {
    return [];
  }
  if (plottable.length === 1) {
    const point = plottable[0];
    return [{ ...point, min: point.value as number, max: point.value as number, avg: point.value as number, band: 0 }];
  }
  const chunkSize = Math.max(1, Math.ceil(plottable.length / targetBuckets));
  const result: RangeTrendPoint[] = [];
  for (let index = 0; index < plottable.length; index += chunkSize) {
    const chunk = plottable.slice(index, index + chunkSize);
    const values = chunk.map((point) => point.value as number);
    const min = Math.min(...values);
    const max = Math.max(...values);
    const avg = values.reduce((sum, value) => sum + value, 0) / values.length;
    const anchor = chunk[chunk.length - 1];
    result.push({
      t: anchor.t,
      time: anchor.time,
      min,
      max,
      avg,
      band: Math.max(0, max - min),
    });
  }
  return result;
}

export function useChartTrendSeries(
  objectPath: string,
  variableName: string,
  valueField: string | undefined,
  refreshIntervalMs: number,
  maxPoints: number,
  historyRange: WidgetHistoryRange = "live",
  mode: ChartSeriesMode = "line"
) {
  const field = valueField ?? "value";
  const isRangeMode = mode === "range";
  const isCandlestickMode = mode === "candlestick";
  const isBucketMode = isRangeMode || isCandlestickMode;
  const tz = useOptionalUserTimeZone();
  const trend = useTrendSeries(
    objectPath,
    variableName,
    valueField,
    refreshIntervalMs,
    maxPoints,
    historyRange,
    isBucketMode
  );

  const aggregateBucket =
    isBucketMode && historyRange !== "live" && isHistoryRange(historyRange)
      ? historyBucketForRangeChart(historyRange)
      : null;
  const calendarRange =
    isHistoryRange(historyRange) && isCalendarHistoryRange(historyRange) ? historyRange : undefined;
  const from =
    aggregateBucket && isHistoryRange(historyRange) && !calendarRange
      ? historyRangeFrom(historyRange)
      : undefined;
  const to =
    calendarRange || (isHistoryRange(historyRange) && historyRange === "all")
      ? undefined
      : new Date().toISOString();

  const aggregateQuery = useQuery({
    queryKey: [
      "variable-history-buckets",
      objectPath,
      variableName,
      field,
      historyRange,
      maxPoints,
      aggregateBucket,
      tz?.timeZone,
    ],
    queryFn: () =>
      fetchVariableHistoryAggregate(objectPath, variableName, {
        field,
        bucket: aggregateBucket as string,
        from,
        to,
        calendarRange,
        timeZone: calendarRange ? tz?.timeZone ?? "UTC" : undefined,
        limit: maxPoints,
      }),
    enabled:
      Boolean(aggregateBucket && objectPath && variableName && trend.historyEnabled),
    staleTime: refreshIntervalMs,
    refetchInterval: refreshIntervalMs,
  });

  const rangePoints = useMemo(() => {
    if (!isRangeMode) {
      return [] as RangeTrendPoint[];
    }
    if (aggregateBucket && aggregateQuery.data?.buckets?.length) {
      return bucketsToRangePoints(aggregateQuery.data.buckets);
    }
    return livePointsToRangePoints(trend.points);
  }, [aggregateBucket, aggregateQuery.data, isRangeMode, trend.points]);

  const candlestickPoints = useMemo(() => {
    if (!isCandlestickMode) {
      return [] as CandlestickPoint[];
    }
    if (aggregateBucket && aggregateQuery.data?.buckets?.length) {
      return bucketsToCandlestickPoints(aggregateQuery.data.buckets);
    }
    return livePointsToCandlestickPoints(trend.points);
  }, [aggregateBucket, aggregateQuery.data, isCandlestickMode, trend.points]);

  const rangeStats = useMemo(() => {
    if (rangePoints.length === 0) {
      return { min: null, max: null, latest: null };
    }
    return {
      min: Math.min(...rangePoints.map((point) => point.min)),
      max: Math.max(...rangePoints.map((point) => point.max)),
      latest: rangePoints[rangePoints.length - 1].avg,
    };
  }, [rangePoints]);

  const ohlcStats = useMemo(() => candlestickStats(candlestickPoints), [candlestickPoints]);

  if (!isBucketMode) {
    return {
      ...trend,
      mode: "line" as const,
      rangePoints: [] as RangeTrendPoint[],
      candlestickPoints: [] as CandlestickPoint[],
    };
  }

  const bucketPoints = isRangeMode ? rangePoints : candlestickPoints;
  const bucketLoading =
    trend.historyLoading ||
    (aggregateBucket != null && aggregateQuery.isLoading && bucketPoints.length === 0);

  const stats = isRangeMode ? rangeStats : ohlcStats;
  const latestValue = isRangeMode
    ? rangePoints[rangePoints.length - 1]?.avg
    : candlestickPoints[candlestickPoints.length - 1]?.close;

  return {
    ...trend,
    mode,
    rangePoints,
    candlestickPoints,
    points: bucketPoints.map((point) => ({
      t: point.t,
      time: point.time,
      value: isRangeMode ? (point as RangeTrendPoint).avg : (point as CandlestickPoint).close,
    })),
    stats,
    historyLoading: bucketLoading,
    isLoading: trend.isLoading || bucketLoading,
    aggregated: aggregateBucket != null,
    historyBucket: aggregateBucket,
    latestValue,
  };
}
