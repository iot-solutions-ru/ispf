import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  ComposedChart,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { ChartWidget } from "../../../types/dashboard";
import { widgetHistoryRangeLabel } from "../../../types/dashboard";
import { useChartTrendSeries } from "../../../hooks/useChartTrendSeries";
import { useWidgetObjectPath } from "../../../hooks/useWidgetObjectPath";
import { useWidgetStyles } from "../widgetStyles";
import WidgetDragHandle from "../WidgetDragHandle";
import WidgetHistoryControls from "../WidgetHistoryControls";
import {
  buildDemoCandlestickPoints,
  buildDemoRangeTrendPoints,
  buildDemoTrendPoints,
  parseDemoPreview,
} from "../widgetDemoPreview";
import CandlestickChartBody from "./CandlestickChartBody";
import ChartBubbleWidgetView from "./ChartBubbleWidgetView";
import ChartRadarWidgetView from "./ChartRadarWidgetView";

interface ChartWidgetViewProps {
  widget: ChartWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function ChartWidgetView({
  widget,
  refreshIntervalMs,
  editable = false,
}: ChartWidgetViewProps) {
  const { t } = useTranslation("widgets");
  const maxPoints = widget.maxPoints ?? 120;
  const historyRange = widget.historyRange ?? "live";
  const color = widget.color ?? "#2f81f7";
  const chartStyle = widget.chartStyle ?? "area";
  const chartType = widget.chartType ?? chartStyle;

  if (chartType === "bubble") {
    return (
      <ChartBubbleWidgetView
        widget={widget}
        refreshIntervalMs={refreshIntervalMs}
        editable={editable}
      />
    );
  }
  if (chartType === "radar") {
    return (
      <ChartRadarWidgetView
        widget={widget}
        refreshIntervalMs={refreshIntervalMs}
        editable={editable}
      />
    );
  }

  const isRangeChart = chartType === "range";
  const isCandlestickChart = chartType === "candlestick";
  const chartMode = isRangeChart ? "range" : isCandlestickChart ? "candlestick" : "line";
  const decimals = widget.decimals ?? 1;
  const objectPath = useWidgetObjectPath(widget.objectPath, widget.selectionKey);
  const styles = useWidgetStyles(widget.stylesJson);

  const series = useChartTrendSeries(
    objectPath,
    widget.variableName ?? "",
    widget.valueField,
    refreshIntervalMs,
    maxPoints,
    historyRange,
    chartMode
  );

  const demoPreview = parseDemoPreview<Array<{ t?: number; v: number }>>(widget.demoPreviewJson);
  const demoLinePoints = useMemo(
    () => (editable && series.points.length < 2 ? buildDemoTrendPoints(demoPreview) : []),
    [demoPreview, editable, series.points.length]
  );
  const demoRangePoints = useMemo(
    () => (editable && isRangeChart && series.rangePoints.length < 2
      ? buildDemoRangeTrendPoints(demoPreview)
      : []),
    [demoPreview, editable, isRangeChart, series.rangePoints.length]
  );
  const demoCandlestickPoints = useMemo(
    () => (editable && isCandlestickChart && series.candlestickPoints.length < 2
      ? buildDemoCandlestickPoints(demoPreview)
      : []),
    [demoPreview, editable, isCandlestickChart, series.candlestickPoints.length]
  );

  const points = demoLinePoints.length >= 2 ? demoLinePoints : series.points;
  const rangePoints =
    demoRangePoints.length >= 2 ? demoRangePoints : series.rangePoints;
  const candlestickPoints =
    demoCandlestickPoints.length >= 2 ? demoCandlestickPoints : series.candlestickPoints;
  const isDemo = isRangeChart
    ? demoRangePoints.length >= 2
    : isCandlestickChart
      ? demoCandlestickPoints.length >= 2
      : demoLinePoints.length >= 2;

  const displayStats = isDemo
    ? isRangeChart
      ? {
          latest: rangePoints[rangePoints.length - 1]?.avg ?? null,
          min: Math.min(...rangePoints.map((point) => point.min)),
          max: Math.max(...rangePoints.map((point) => point.max)),
        }
      : isCandlestickChart
        ? {
            latest: candlestickPoints[candlestickPoints.length - 1]?.close ?? null,
            min: Math.min(...candlestickPoints.map((point) => point.low)),
            max: Math.max(...candlestickPoints.map((point) => point.high)),
          }
        : {
            latest: points[points.length - 1]?.value ?? null,
            min: Math.min(...points.map((point) => point.value)),
            max: Math.max(...points.map((point) => point.value)),
          }
    : series.stats;

  const unitRow = series.variable?.value?.rows[0];
  const unit =
    widget.unit ??
    (widget.unitField && unitRow ? String(unitRow[widget.unitField] ?? "") : "");

  const chartData = isRangeChart ? rangePoints : isCandlestickChart ? candlestickPoints : points;
  const chartReady = chartData.length >= 1;
  const bandLabel =
    isRangeChart && series.historyBucket
      ? t("view.chartRange.bandPerBucket", { bucket: series.historyBucket })
      : isRangeChart && historyRange === "live"
        ? t("view.chartRange.bandLive")
        : null;
  const candlestickLabel =
    isCandlestickChart && series.historyBucket
      ? t("view.chartCandlestick.perBucket", { bucket: series.historyBucket })
      : isCandlestickChart && historyRange === "live"
        ? t("view.chartCandlestick.liveWindow")
        : null;

  return (
    <div className="dash-widget dash-widget-chart" style={styles.card}>
      <WidgetDragHandle visible={editable} />
      <div className="dash-widget-chart-head" style={styles.body}>
        <div className="dash-widget-title" style={styles.title}>
          {widget.title}
          {isDemo ? <span className="dash-widget-demo-badge">{t("view.demoBadge")}</span> : null}
        </div>
        <div className="dash-chart-head-side">
          <div className="dash-chart-stats">
            {displayStats.latest != null ? (
              <span className="dash-chart-latest" style={styles.value}>
                {displayStats.latest.toFixed(decimals)}
                {unit ? ` ${unit}` : ""}
              </span>
            ) : (
              <span className="dash-chart-latest muted">—</span>
            )}
            {displayStats.min != null && displayStats.max != null && (
              <span className="dash-chart-range">
                min {displayStats.min.toFixed(decimals)} · max {displayStats.max.toFixed(decimals)}
                {isRangeChart && series.aggregated && series.historyBucket ? ` · ${bandLabel}` : ""}
                {isCandlestickChart && candlestickLabel ? ` · ${candlestickLabel}` : ""}
                {!isRangeChart && !isCandlestickChart && series.aggregated && series.historyBucket
                  ? ` · avg/${series.historyBucket}`
                  : ""}
              </span>
            )}
          </div>
          <WidgetHistoryControls
            objectPath={objectPath}
            variableName={widget.variableName ?? ""}
            valueField={widget.valueField}
            title={widget.title}
            historyEnabled={series.historyEnabled}
            historyRangeLabel={
              historyRange !== "live" ? widgetHistoryRangeLabel(historyRange, t) : undefined
            }
          />
        </div>
      </div>
      <div className="dash-chart-body" style={styles.chart}>
        {!objectPath && widget.selectionKey ? (
          <div className="dash-chart-placeholder">{t("view.selectDevice")}</div>
        ) : series.historyLoading && chartData.length === 0 && !isDemo ? (
          <div className="dash-chart-placeholder">{t("view.collectingData")}</div>
        ) : series.isError && !isDemo ? (
          <div className="dash-chart-placeholder error">{t("view.bindingError")}</div>
        ) : !chartReady ? (
          <div className="dash-chart-placeholder">
            {series.variable?.historyEnabled === false
              ? t("view.historyDisabled")
              : t("view.waitingTrend")}
          </div>
        ) : isCandlestickChart ? (
          <CandlestickChartBody
            data={candlestickPoints}
            decimals={decimals}
            unit={unit}
            upColor={color}
            t={t}
          />
        ) : isRangeChart ? (
          <ResponsiveContainer width="100%" height="100%">
            <ComposedChart data={rangePoints} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.08)" />
              <XAxis dataKey="time" tick={{ fontSize: 10 }} minTickGap={24} />
              <YAxis
                tick={{ fontSize: 10 }}
                width={42}
                domain={["auto", "auto"]}
                tickFormatter={(v) => Number(v).toFixed(decimals)}
              />
              <Tooltip
                formatter={(value, name) => {
                  const numeric = typeof value === "number" ? value : Number(value);
                  const label =
                    name === "min"
                      ? t("view.chartRange.min")
                      : name === "max"
                        ? t("view.chartRange.max")
                        : name === "avg"
                          ? t("view.chartRange.avg")
                          : String(name);
                  return [`${numeric.toFixed(decimals)}${unit ? ` ${unit}` : ""}`, label];
                }}
                labelFormatter={(label) => t("view.timeLabel", { label })}
                contentStyle={{
                  background: "#161b22",
                  border: "1px solid #30363d",
                  borderRadius: 8,
                }}
              />
              <Area
                dataKey="min"
                stackId="band"
                stroke="none"
                fill="transparent"
                isAnimationActive={false}
              />
              <Area
                dataKey="band"
                stackId="band"
                stroke="none"
                fill={color}
                fillOpacity={0.22}
                isAnimationActive={false}
              />
              <Line
                type="monotone"
                dataKey="avg"
                stroke={color}
                strokeWidth={2}
                dot={false}
                isAnimationActive={false}
              />
            </ComposedChart>
          </ResponsiveContainer>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            {chartType === "bar" ? (
              <BarChart data={points} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.08)" />
                <XAxis dataKey="time" tick={{ fontSize: 10 }} minTickGap={24} />
                <YAxis tick={{ fontSize: 10 }} width={42} />
                <Tooltip />
                <Bar dataKey="value" fill={color} isAnimationActive={false} />
              </BarChart>
            ) : chartStyle === "line" ? (
              <LineChart data={points} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.08)" />
                <XAxis dataKey="time" tick={{ fontSize: 10 }} minTickGap={24} />
                <YAxis
                  tick={{ fontSize: 10 }}
                  width={42}
                  domain={["auto", "auto"]}
                  tickFormatter={(v) => Number(v).toFixed(decimals)}
                />
                <Tooltip
                  formatter={(value) => {
                    const numeric = typeof value === "number" ? value : Number(value);
                    return [
                      `${numeric.toFixed(decimals)}${unit ? ` ${unit}` : ""}`,
                      widget.title,
                    ];
                  }}
                  labelFormatter={(label) => t("view.timeLabel", { label })}
                  contentStyle={{
                    background: "#161b22",
                    border: "1px solid #30363d",
                    borderRadius: 8,
                  }}
                />
                <Line
                  type="monotone"
                  dataKey="value"
                  stroke={color}
                  strokeWidth={2}
                  dot={false}
                  isAnimationActive={false}
                />
              </LineChart>
            ) : (
              <AreaChart data={points} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.08)" />
                <XAxis dataKey="time" tick={{ fontSize: 10 }} minTickGap={24} />
                <YAxis
                  tick={{ fontSize: 10 }}
                  width={42}
                  domain={["auto", "auto"]}
                  tickFormatter={(v) => Number(v).toFixed(decimals)}
                />
                <Tooltip
                  formatter={(value) => {
                    const numeric = typeof value === "number" ? value : Number(value);
                    return [
                      `${numeric.toFixed(decimals)}${unit ? ` ${unit}` : ""}`,
                      widget.title,
                    ];
                  }}
                  labelFormatter={(label) => t("view.timeLabel", { label })}
                  contentStyle={{
                    background: "#161b22",
                    border: "1px solid #30363d",
                    borderRadius: 8,
                  }}
                />
                <Area
                  type="monotone"
                  dataKey="value"
                  stroke={color}
                  fill={color}
                  fillOpacity={0.18}
                  strokeWidth={2}
                  isAnimationActive={false}
                />
              </AreaChart>
            )}
          </ResponsiveContainer>
        )}
      </div>
    </div>
  );
}
