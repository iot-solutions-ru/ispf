import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Alert, Button, Segmented, Select, Space, Typography } from "antd";
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { downloadVariableHistoryExport } from "../../api";
import { CHART_GRID_STROKE, CHART_TOOLTIP_STYLE } from "../../utils/analytics/chartTheme";
import {
  type HistoryRange,
  historyRangeFrom,
  useVariableHistory,
} from "../../hooks/useVariableHistory";

interface VariableHistoryPanelProps {
  objectPath: string;
  variableName: string;
  /** Numeric schema fields available for history (from variable definition). */
  fields?: string[];
  refreshIntervalMs?: number;
}

export default function VariableHistoryPanel({
  objectPath,
  variableName,
  fields = ["value"],
  refreshIntervalMs = 15_000,
}: VariableHistoryPanelProps) {
  const { t } = useTranslation("inspector");
  const rangeOptions: { id: HistoryRange; labelKey: string }[] = [
    { id: "1h", labelKey: "variables.historyPanel.range1h" },
    { id: "6h", labelKey: "variables.historyPanel.range6h" },
    { id: "24h", labelKey: "variables.historyPanel.range24h" },
    { id: "today", labelKey: "variables.historyPanel.rangeToday" },
    { id: "yesterday", labelKey: "variables.historyPanel.rangeYesterday" },
    { id: "7d", labelKey: "variables.historyPanel.range7d" },
    { id: "all", labelKey: "variables.historyPanel.rangeAll" },
  ];
  const [range, setRange] = useState<HistoryRange>("24h");
  const [field, setField] = useState(fields[0] ?? "value");
  const [exportError, setExportError] = useState<string | null>(null);
  const [exporting, setExporting] = useState<"csv" | "json" | null>(null);

  useEffect(() => {
    if (!fields.includes(field)) {
      setField(fields[0] ?? "value");
    }
  }, [fields, field]);

  const { points, textSamples, stats, isLoading, isError, error, aggregated, bucket, isRecordSnapshot } =
    useVariableHistory(
    objectPath,
    variableName,
    {
      field,
      range,
      limit: 1000,
      refreshIntervalMs,
    }
  );

  const exportHistory = async (format: "csv" | "json") => {
    setExportError(null);
    setExporting(format);
    try {
      const from = historyRangeFrom(range);
      await downloadVariableHistoryExport(objectPath, variableName, {
        format,
        field,
        from,
        to: new Date().toISOString(),
        limit: 10_000,
      });
    } catch (err) {
      setExportError(String(err));
    } finally {
      setExporting(null);
    }
  };

  return (
    <div className="variable-history-panel">
      <div className="variable-history-toolbar">
        <div className="variable-history-stats">
          {isRecordSnapshot ? (
            textSamples.length > 0 ? (
              <span className="variable-history-range">
                {textSamples.length} {t("variables.historyPanel.snapshots")}
              </span>
            ) : (
              <Typography.Text type="secondary">{t("variables.historyPanel.noData")}</Typography.Text>
            )
          ) : stats.latest != null ? (
            <>
              <span className="variable-history-latest">{stats.latest.toFixed(2)}</span>
              {stats.min != null && stats.max != null && (
                <span className="variable-history-range">
                  min {stats.min.toFixed(2)} · max {stats.max.toFixed(2)}
                  {aggregated && bucket && (
                    <span className="variable-history-aggregate-hint"> · avg/{bucket}</span>
                  )}
                </span>
              )}
            </>
          ) : (
            <Typography.Text type="secondary">{t("variables.historyPanel.noData")}</Typography.Text>
          )}
        </div>
        <div className="variable-history-controls">
          {fields.length > 1 && (
            <label className="variable-history-field-select">
              <span className="sr-only">{t("variables.historyPanel.field")}</span>
              <Select
                size="small"
                value={field}
                onChange={setField}
                options={fields.map((item) => ({ label: item, value: item }))}
              />
            </label>
          )}
          <Segmented
            size="small"
            value={range}
            onChange={(value) => setRange(value as HistoryRange)}
            options={rangeOptions.map((option) => ({ label: t(option.labelKey), value: option.id }))}
          />
          <Space.Compact className="variable-history-export">
            <Button
              size="small"
              disabled={exporting != null}
              loading={exporting === "csv"}
              onClick={() => exportHistory("csv")}
            >
              CSV
            </Button>
            <Button
              size="small"
              disabled={exporting != null}
              loading={exporting === "json"}
              onClick={() => exportHistory("json")}
            >
              JSON
            </Button>
          </Space.Compact>
        </div>
      </div>

      {exportError && <Alert type="error" message={exportError} showIcon className="variable-history-export-error" />}

      <div className={isRecordSnapshot ? "variable-history-snapshots" : "variable-history-chart"}>
        {isRecordSnapshot ? (
          isLoading && textSamples.length === 0 ? (
            <p className="hint">{t("variables.historyPanel.loading")}</p>
          ) : isError ? (
            <p className="hint error">{(error as Error).message}</p>
          ) : textSamples.length === 0 ? (
            <p className="hint">{t("variables.historyPanel.noData")}</p>
          ) : (
            <ul className="variable-history-snapshot-list">
              {textSamples.map((sample) => {
                let display = sample.text;
                try {
                  display = JSON.stringify(JSON.parse(sample.text), null, 2);
                } catch {
                  // keep raw text
                }
                return (
                  <li key={sample.ts} className="variable-history-snapshot-item">
                    <time className="variable-history-snapshot-time">{sample.time}</time>
                    <pre className="variable-history-snapshot-json">{display}</pre>
                  </li>
                );
              })}
            </ul>
          )
        ) : isLoading && points.length === 0 ? (
          <p className="hint">{t("variables.historyPanel.loading")}</p>
        ) : isError ? (
          <p className="hint error">{(error as Error).message}</p>
        ) : points.length < 2 ? (
          <p className="hint">{t("variables.historyPanel.notEnoughPoints")}</p>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={points}>
              <CartesianGrid strokeDasharray="3 3" stroke={CHART_GRID_STROKE} />
              <XAxis
                dataKey="time"
                tick={{ fontSize: 10 }}
                minTickGap={24}
                interval="preserveStartEnd"
              />
              <YAxis tick={{ fontSize: 10 }} width={48} domain={["auto", "auto"]} />
              <Tooltip contentStyle={{ ...CHART_TOOLTIP_STYLE, fontSize: "0.8rem" }} />
              <Line
                type="monotone"
                dataKey="value"
                stroke="var(--accent)"
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
