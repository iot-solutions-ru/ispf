import { useState } from "react";
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import {
  type HistoryRange,
  useVariableHistory,
} from "../hooks/useVariableHistory";

const RANGE_OPTIONS: { id: HistoryRange; label: string }[] = [
  { id: "1h", label: "1 ч" },
  { id: "6h", label: "6 ч" },
  { id: "24h", label: "24 ч" },
  { id: "7d", label: "7 д" },
  { id: "all", label: "Всё" },
];

interface VariableHistoryPanelProps {
  objectPath: string;
  variableName: string;
  valueField?: string;
  refreshIntervalMs?: number;
}

export default function VariableHistoryPanel({
  objectPath,
  variableName,
  valueField,
  refreshIntervalMs = 15_000,
}: VariableHistoryPanelProps) {
  const [range, setRange] = useState<HistoryRange>("24h");
  const { points, stats, isLoading, isError, error } = useVariableHistory(
    objectPath,
    variableName,
    {
      field: valueField,
      range,
      limit: 1000,
      refreshIntervalMs,
    }
  );

  return (
    <div className="variable-history-panel">
      <div className="variable-history-toolbar">
        <div className="variable-history-stats">
          {stats.latest != null ? (
            <>
              <span className="variable-history-latest">{stats.latest.toFixed(2)}</span>
              {stats.min != null && stats.max != null && (
                <span className="variable-history-range">
                  min {stats.min.toFixed(2)} · max {stats.max.toFixed(2)}
                </span>
              )}
            </>
          ) : (
            <span className="hint">Нет данных за выбранный период</span>
          )}
        </div>
        <div className="variable-history-ranges">
          {RANGE_OPTIONS.map((option) => (
            <button
              key={option.id}
              type="button"
              className={`btn tiny ${range === option.id ? "primary" : ""}`}
              onClick={() => setRange(option.id)}
            >
              {option.label}
            </button>
          ))}
        </div>
      </div>

      <div className="variable-history-chart">
        {isLoading && points.length === 0 ? (
          <p className="hint">Загрузка истории…</p>
        ) : isError ? (
          <p className="hint error">{(error as Error).message}</p>
        ) : points.length < 2 ? (
          <p className="hint">Недостаточно точек для графика</p>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={points}>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.08)" />
              <XAxis
                dataKey="time"
                tick={{ fontSize: 10 }}
                minTickGap={24}
                interval="preserveStartEnd"
              />
              <YAxis tick={{ fontSize: 10 }} width={48} domain={["auto", "auto"]} />
              <Tooltip
                contentStyle={{
                  background: "var(--bg-elevated)",
                  border: "1px solid var(--border)",
                  fontSize: "0.8rem",
                }}
              />
              <Line
                type="monotone"
                dataKey="value"
                stroke="#2f81f7"
                strokeWidth={2}
                dot={false}
                isAnimationActive={false}
              />
            </LineChart>
          </ResponsiveContainer>
        )}
      </div>
    </div>
  );
}
