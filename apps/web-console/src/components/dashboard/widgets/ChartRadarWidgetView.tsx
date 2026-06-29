import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import {
  PolarAngleAxis,
  PolarGrid,
  PolarRadiusAxis,
  Radar,
  RadarChart,
  ResponsiveContainer,
  Tooltip,
} from "recharts";
import type { ChartWidget } from "../../../types/dashboard";
import { useChartRadarSeries } from "../../../hooks/useChartRadarSeries";
import { useWidgetObjectPath } from "../../../hooks/useWidgetObjectPath";
import { useWidgetStyles } from "../widgetStyles";
import { CHART_POLAR_GRID_STROKE, CHART_TOOLTIP_STYLE } from "../../../utils/chartTheme";
import WidgetDragHandle from "../WidgetDragHandle";
import { parseDemoPreview } from "../widgetDemoPreview";
import { parseDemoRadarRows } from "../../../utils/chartRadarBubbleUtils";

interface ChartRadarWidgetViewProps {
  widget: ChartWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function ChartRadarWidgetView({
  widget,
  refreshIntervalMs,
  editable = false,
}: ChartRadarWidgetViewProps) {
  const { t } = useTranslation("widgets");
  const styles = useWidgetStyles(widget.stylesJson);
  const decimals = widget.decimals ?? 1;
  const color = widget.color ?? "#2f81f7";
  const objectPath = useWidgetObjectPath(widget.objectPath, widget.selectionKey);

  const series = useChartRadarSeries(objectPath, widget, refreshIntervalMs);
  const demoPreview = parseDemoPreview<unknown>(widget.demoPreviewJson);
  const demoRows = useMemo(
    () => (editable && series.rows.length < 3 ? parseDemoRadarRows(demoPreview) : []),
    [demoPreview, editable, series.rows.length]
  );
  const rows = demoRows.length >= 3 ? demoRows : series.rows;
  const isDemo = demoRows.length >= 3;
  const chartReady = rows.length >= 3;

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
            {series.axes.length > 0
              ? t("view.chartRadar.axesCount", { count: series.axes.length })
              : t("view.chartRadar.noAxes")}
            {series.partial && !isDemo ? ` · ${t("view.chartRadar.partial")}` : ""}
          </span>
        </div>
      </div>
      <div className="dash-chart-body" style={styles.chart}>
        {!objectPath && widget.selectionKey ? (
          <div className="dash-chart-placeholder">{t("view.selectDevice")}</div>
        ) : series.axes.length === 0 && !isDemo ? (
          <div className="dash-chart-placeholder">{t("view.chartRadar.configureAxes")}</div>
        ) : series.isLoading && !isDemo ? (
          <div className="dash-chart-placeholder">{t("view.collectingData")}</div>
        ) : series.isError && !isDemo ? (
          <div className="dash-chart-placeholder error">{t("view.bindingError")}</div>
        ) : !chartReady ? (
          <div className="dash-chart-placeholder">{t("view.chartRadar.waitingData")}</div>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <RadarChart cx="50%" cy="50%" outerRadius="78%" data={rows}>
              <PolarGrid stroke={CHART_POLAR_GRID_STROKE} />
              <PolarAngleAxis dataKey="subject" tick={{ fontSize: 10 }} />
              <PolarRadiusAxis
                angle={30}
                domain={[0, "auto"]}
                tick={{ fontSize: 9 }}
                tickFormatter={(v) => Number(v).toFixed(decimals)}
              />
              <Radar
                name={widget.title}
                dataKey="value"
                stroke={color}
                fill={color}
                fillOpacity={0.28}
                isAnimationActive={false}
              />
              <Tooltip
                formatter={(value) => {
                  const numeric = typeof value === "number" ? value : Number(value);
                  return [
                    Number.isFinite(numeric) ? numeric.toFixed(decimals) : "—",
                    widget.title,
                  ];
                }}
                contentStyle={CHART_TOOLTIP_STYLE}
              />
            </RadarChart>
          </ResponsiveContainer>
        )}
      </div>
    </div>
  );
}
