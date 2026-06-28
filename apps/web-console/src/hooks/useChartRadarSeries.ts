import { useMemo } from "react";
import type { ChartWidget } from "../types/dashboard";
import { useVariablesQuery } from "./useVariablesQuery";
import {
  buildRadarRows,
  parseRadarAxesJson,
  type ChartRadarRow,
} from "../utils/chartRadarBubbleUtils";

export function useChartRadarSeries(
  objectPath: string,
  widget: Pick<ChartWidget, "radarAxesJson">,
  refreshIntervalMs: number
) {
  const axes = useMemo(() => parseRadarAxesJson(widget.radarAxesJson), [widget.radarAxesJson]);
  const enabled = Boolean(objectPath && axes.length > 0);

  const variablesQuery = useVariablesQuery(objectPath, refreshIntervalMs, enabled);

  const rows: ChartRadarRow[] = useMemo(
    () => (enabled ? buildRadarRows(axes, variablesQuery.data) : []),
    [enabled, axes, variablesQuery.data]
  );

  return {
    axes,
    rows,
    isLoading: variablesQuery.isLoading,
    isError: variablesQuery.isError,
    ready: rows.length >= 3,
    partial: rows.length > 0 && rows.length < axes.length,
  };
}
