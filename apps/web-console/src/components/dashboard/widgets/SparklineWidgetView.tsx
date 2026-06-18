import { Line, LineChart, ResponsiveContainer } from "recharts";
import type { SparklineWidget } from "../../../types/dashboard";
import { useTrendSeries } from "../../../hooks/useTrendSeries";
import WidgetDragHandle from "../WidgetDragHandle";

interface SparklineWidgetViewProps {
  widget: SparklineWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function SparklineWidgetView({
  widget,
  refreshIntervalMs,
  editable = false,
}: SparklineWidgetViewProps) {
  const maxPoints = widget.maxPoints ?? 40;
  const color = widget.color ?? "#3fb950";
  const decimals = widget.decimals ?? 1;

  const { points, stats, isLoading } = useTrendSeries(
    widget.objectPath,
    widget.variableName,
    widget.valueField,
    refreshIntervalMs,
    maxPoints
  );

  return (
    <div className="dash-widget dash-widget-sparkline">
      <WidgetDragHandle visible={editable} />
      <div className="dash-widget-title">{widget.title}</div>
      <div className="dash-sparkline-body">
        <div className="dash-sparkline-value">
          {stats.latest != null ? stats.latest.toFixed(decimals) : isLoading ? "…" : "—"}
        </div>
        <div className="dash-sparkline-chart">
          {points.length >= 2 ? (
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={points}>
                <Line
                  type="monotone"
                  dataKey="value"
                  stroke={color}
                  strokeWidth={2}
                  dot={false}
                  isAnimationActive={false}
                />
              </LineChart>
            </ResponsiveContainer>
          ) : (
            <span className="dash-chart-placeholder">…</span>
          )}
        </div>
      </div>
    </div>
  );
}
