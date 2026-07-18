import { useMemo } from "react";
import type { ChartWidget } from "../types/dashboard";
import { useBoundVariable } from "./useBoundVariable";
import { useTrendSeries } from "./useTrendSeries";
import { useVariablesQuery } from "./useVariablesQuery";
import {
  buildBubbleSnapshotPoints,
  parseBubblePointsJson,
  zipBubbleTrajectoryPoints,
  type ChartBubblePoint,
} from "../utils/analytics/chartRadarBubbleUtils";

export function useChartBubbleSeries(
  objectPath: string,
  widget: Pick<
    ChartWidget,
    | "bubbleXVariable"
    | "bubbleYVariable"
    | "bubbleSizeVariable"
    | "bubbleDefaultSize"
    | "bubblePointsJson"
    | "valueField"
    | "maxPoints"
    | "historyRange"
  >,
  refreshIntervalMs: number
) {
  const defaultSize = widget.bubbleDefaultSize ?? 80;
  const maxPoints = widget.maxPoints ?? 120;
  const historyRange = widget.historyRange ?? "live";
  const pointConfigs = useMemo(
    () => parseBubblePointsJson(widget.bubblePointsJson),
    [widget.bubblePointsJson]
  );
  const snapshotMode = pointConfigs.length > 0;
  const trajectoryMode =
    !snapshotMode && Boolean(widget.bubbleXVariable) && Boolean(widget.bubbleYVariable);

  const variablesQuery = useVariablesQuery(
    objectPath,
    refreshIntervalMs,
    Boolean(objectPath && snapshotMode)
  );

  const xTrend = useTrendSeries(
    objectPath,
    widget.bubbleXVariable ?? "",
    widget.valueField,
    refreshIntervalMs,
    maxPoints,
    historyRange
  );
  const yTrend = useTrendSeries(
    objectPath,
    widget.bubbleYVariable ?? "",
    widget.valueField,
    refreshIntervalMs,
    maxPoints,
    historyRange
  );
  const sizeBinding = useBoundVariable(
    objectPath,
    widget.bubbleSizeVariable ?? "",
    widget.valueField,
    refreshIntervalMs
  );

  const snapshotPoints = useMemo(
    () =>
      snapshotMode
        ? buildBubbleSnapshotPoints(pointConfigs, variablesQuery.data, defaultSize)
        : [],
    [snapshotMode, pointConfigs, variablesQuery.data, defaultSize]
  );

  const trajectoryPoints = useMemo(
    () =>
      trajectoryMode
        ? zipBubbleTrajectoryPoints(
            xTrend.points,
            yTrend.points,
            sizeBinding.rawValue,
            defaultSize
          )
        : [],
    [
      trajectoryMode,
      xTrend.points,
      yTrend.points,
      sizeBinding.rawValue,
      defaultSize,
    ]
  );

  const points: ChartBubblePoint[] = snapshotPoints.length > 0 ? snapshotPoints : trajectoryPoints;

  const isLoading =
    snapshotMode ? variablesQuery.isLoading : xTrend.isLoading || yTrend.isLoading;
  const isError = snapshotMode ? variablesQuery.isError : xTrend.isError || yTrend.isError;

  return {
    points,
    snapshotMode,
    trajectoryMode,
    isLoading,
    isError,
    ready: points.length > 0,
    xVariable: xTrend.variable,
    yVariable: yTrend.variable,
  };
}
