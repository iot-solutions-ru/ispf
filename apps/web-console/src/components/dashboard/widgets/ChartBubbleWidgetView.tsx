import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import {
  CartesianGrid,
  ResponsiveContainer,
  Scatter,
  ScatterChart,
  Tooltip,
  XAxis,
  YAxis,
  ZAxis,
} from "recharts";
import type { ChartWidget } from "../../../types/dashboard";
import { useChartBubbleSeries } from "../../../hooks/useChartBubbleSeries";
import { useWidgetObjectPath } from "../../../hooks/useWidgetObjectPath";
import { useWidgetStyles } from "../widgetStyles";
import { CHART_GRID_STROKE, CHART_TOOLTIP_STYLE } from "../../../utils/analytics/chartTheme";
import WidgetDragHandle from "../WidgetDragHandle";
import { parseDemoPreview } from "../widgetDemoPreview";
import { parseDemoBubblePoints } from "../../../utils/analytics/chartRadarBubbleUtils";

interface ChartBubbleWidgetViewProps {
  widget: ChartWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function ChartBubbleWidgetView({
  widget,
  refreshIntervalMs,
  editable = false,
}: ChartBubbleWidgetViewProps) {
  const { t } = useTranslation("widgets");
  const styles = useWidgetStyles(widget.stylesJson);
  const decimals = widget.decimals ?? 1;
  const color = widget.color ?? "#2f81f7";
  const objectPath = useWidgetObjectPath(widget.objectPath, widget.selectionKey);

  const series = useChartBubbleSeries(objectPath, widget, refreshIntervalMs);
  const demoPreview = parseDemoPreview<unknown>(widget.demoPreviewJson);
  const demoPoints = useMemo(
    () =>
      editable && series.points.length === 0 ? parseDemoBubblePoints(demoPreview) : [],
    [demoPreview, editable, series.points.length]
  );
  const points = demoPoints.length > 0 ? demoPoints : series.points;
  const isDemo = demoPoints.length > 0;
  const chartReady = points.length > 0;

  return (
    <div className="dash-widget dash-widget-chart" style={styles.card}>
      <WidgetDragHandle visible={editable} />
      <div className="dash-widget-chart-head" style={styles.body}>
        <div className="dash-widget-title" style={styles.title}>
          {widget.title}
          {isDemo ? <span className="dash-widget-demo-badge">{t("view.demoBadge")}</span> : null}
        </div>
        <div className="dash-chart-head-side">
          <span className="dash-chart-range muted">
            {series.snapshotMode
              ? t("view.chartBubble.snapshotMode")
              : t("view.chartBubble.trajectoryMode")}
            {chartReady ? ` · ${points.length} pt` : ""}
          </span>
        </div>
      </div>
      <div className="dash-chart-body" style={styles.chart}>
        {!objectPath && widget.selectionKey ? (
          <div className="dash-chart-placeholder">{t("view.selectDevice")}</div>
        ) : series.isLoading && !isDemo ? (
          <div className="dash-chart-placeholder">{t("view.collectingData")}</div>
        ) : series.isError && !isDemo ? (
          <div className="dash-chart-placeholder error">{t("view.bindingError")}</div>
        ) : !chartReady ? (
          <div className="dash-chart-placeholder">{t("view.chartBubble.waitingData")}</div>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <ScatterChart margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke={CHART_GRID_STROKE} />
              <XAxis
                type="number"
                dataKey="x"
                name={t("view.chartBubble.axisX")}
                tick={{ fontSize: 10 }}
                tickFormatter={(v) => Number(v).toFixed(decimals)}
              />
              <YAxis
                type="number"
                dataKey="y"
                name={t("view.chartBubble.axisY")}
                tick={{ fontSize: 10 }}
                width={42}
                tickFormatter={(v) => Number(v).toFixed(decimals)}
              />
              <ZAxis type="number" dataKey="z" range={[60, 400]} name={t("view.chartBubble.axisSize")} />
              <Tooltip
                cursor={{ strokeDasharray: "3 3" }}
                formatter={(value, name) => {
                  const numeric = typeof value === "number" ? value : Number(value);
                  return [Number.isFinite(numeric) ? numeric.toFixed(decimals) : "—", String(name)];
                }}
                labelFormatter={(_, payload) => {
                  const row = payload?.[0]?.payload as { name?: string } | undefined;
                  return row?.name ?? "";
                }}
                contentStyle={CHART_TOOLTIP_STYLE}
              />
              <Scatter name={widget.title} data={points} fill={color} isAnimationActive={false} />
            </ScatterChart>
          </ResponsiveContainer>
        )}
      </div>
    </div>
  );
}
