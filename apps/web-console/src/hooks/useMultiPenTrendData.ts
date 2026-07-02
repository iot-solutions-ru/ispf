import { useMemo } from "react";
import { useQueries } from "@tanstack/react-query";
import { fetchVariableHistory } from "../api";
import type { TrendPen } from "../types/trendPen";
import {
  historyRangeFrom,
  isCalendarHistoryRange,
  type HistoryRange,
} from "./useVariableHistory";
import { useOptionalUserTimeZone } from "../context/UserTimeZoneContext";

export interface MergedTrendPoint {
  t: number;
  time: string;
  [penId: string]: number | string;
}

const HISTORY_LIMIT = 1000;

function sampleToPoint(ts: string, value: number): { t: number; time: string; value: number } {
  const t = Date.parse(ts);
  const resolved = Number.isFinite(t) ? t : Date.now();
  return {
    t: resolved,
    time: new Date(resolved).toLocaleTimeString(),
    value,
  };
}

function mergePenSeries(
  pens: TrendPen[],
  seriesByPenId: Record<string, Array<{ t: number; time: string; value: number }>>
): MergedTrendPoint[] {
  const timeMap = new Map<number, MergedTrendPoint>();
  for (const pen of pens) {
    const points = seriesByPenId[pen.id] ?? [];
    for (const point of points) {
      let row = timeMap.get(point.t);
      if (!row) {
        row = { t: point.t, time: point.time };
        timeMap.set(point.t, row);
      }
      row[pen.id] = point.value;
    }
  }
  return [...timeMap.values()].sort((left, right) => left.t - right.t);
}

export function useMultiPenTrendData(pens: TrendPen[], range: HistoryRange, refreshIntervalMs = 30_000) {
  const tz = useOptionalUserTimeZone();
  const calendarRange = isCalendarHistoryRange(range) ? range : undefined;
  const from = calendarRange ? undefined : historyRangeFrom(range);
  const to =
    calendarRange || range === "all" ? undefined : new Date().toISOString();

  const queries = useQueries({
    queries: pens.map((pen) => ({
      queryKey: [
        "multi-pen-history",
        pen.id,
        range,
        tz?.timeZone,
      ],
      queryFn: async () => {
        const field = pen.valueField ?? "value";
        const response = await fetchVariableHistory(pen.objectPath, pen.variableName, {
          field,
          from,
          to,
          calendarRange,
          timeZone: calendarRange ? tz?.timeZone ?? "UTC" : undefined,
          limit: HISTORY_LIMIT,
        });
        return response.samples
          .map((sample) => {
            const numeric = sample.value;
            if (numeric == null || !Number.isFinite(numeric)) {
              return null;
            }
            return sampleToPoint(sample.ts, numeric);
          })
          .filter((point): point is { t: number; time: string; value: number } => point != null);
      },
      enabled: Boolean(pen.objectPath && pen.variableName),
      staleTime: refreshIntervalMs,
      refetchInterval: refreshIntervalMs,
    })),
  });

  const seriesByPenId = useMemo(() => {
    const result: Record<string, Array<{ t: number; time: string; value: number }>> = {};
    pens.forEach((pen, index) => {
      result[pen.id] = queries[index]?.data ?? [];
    });
    return result;
  }, [pens, queries]);

  const merged = useMemo(
    () => mergePenSeries(pens, seriesByPenId),
    [pens, seriesByPenId]
  );

  const isLoading = queries.some((query) => query.isLoading);
  const isError = queries.some((query) => query.isError);
  const error = queries.find((query) => query.error)?.error ?? null;

  return {
    merged,
    seriesByPenId,
    isLoading,
    isError,
    error,
    pointLimit: HISTORY_LIMIT,
  };
}
