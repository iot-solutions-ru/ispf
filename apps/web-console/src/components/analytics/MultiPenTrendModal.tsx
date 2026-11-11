import { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Alert, Button, Modal, Segmented, Select, Tag, Typography } from "antd";
import {
  Brush,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { CHART_GRID_STROKE, CHART_TOOLTIP_STYLE } from "../../utils/analytics/chartTheme";
import { useMultiPenTrendData } from "../../hooks/useMultiPenTrendData";
import type { HistoryRange } from "../../hooks/useVariableHistory";
import {
  MAX_TREND_PENS,
  TREND_PEN_COLORS,
  type TrendPen,
  createTrendPen,
  trendPenKey,
} from "../../types/trendPen";
import {
  defaultTrendAxis,
  normalizedPenKey,
  withNormalizedPens,
  type TrendAxis,
  type TrendScaleMode,
} from "./trendScale";

interface MultiPenTrendModalProps {
  pens: TrendPen[];
  availablePens?: TrendPen[];
  onClose: () => void;
}

export default function MultiPenTrendModal({
  pens: initialPens,
  availablePens = [],
  onClose,
}: MultiPenTrendModalProps) {
  const { t } = useTranslation(["widgets", "inspector", "common"]);
  const rangeOptions: { id: HistoryRange; labelKey: string }[] = [
    { id: "1h", labelKey: "variables.historyPanel.range1h" },
    { id: "6h", labelKey: "variables.historyPanel.range6h" },
    { id: "24h", labelKey: "variables.historyPanel.range24h" },
    { id: "today", labelKey: "variables.historyPanel.rangeToday" },
    { id: "7d", labelKey: "variables.historyPanel.range7d" },
  ];
  const [range, setRange] = useState<HistoryRange>("24h");
  const [pens, setPens] = useState<TrendPen[]>(() => initialPens.slice(0, MAX_TREND_PENS));
  const [scaleMode, setScaleMode] = useState<TrendScaleMode>("shared");
  const [axisByPenId, setAxisByPenId] = useState<Record<string, TrendAxis>>({});
  const [brushRange, setBrushRange] = useState<{ startIndex?: number; endIndex?: number }>({});
  const [exportError, setExportError] = useState<string | null>(null);

  const { merged, isLoading, isError, error, pointLimit } = useMultiPenTrendData(
    pens,
    range,
    30_000
  );

  const visibleData = useMemo(() => {
    if (brushRange.startIndex == null || brushRange.endIndex == null) {
      return merged;
    }
    return merged.slice(brushRange.startIndex, brushRange.endIndex + 1);
  }, [brushRange.endIndex, brushRange.startIndex, merged]);

  const chartData = useMemo(
    () => (scaleMode === "normalized" ? withNormalizedPens(visibleData, pens.map((pen) => pen.id)) : visibleData),
    [pens, scaleMode, visibleData],
  );

  const axisFor = (penId: string, index: number): TrendAxis => axisByPenId[penId] ?? defaultTrendAxis(index);
  const dualGridAxis: TrendAxis = pens.some((pen, index) => axisFor(pen.id, index) === "left") ? "left" : "right";

  const addablePens = useMemo(
    () =>
      availablePens.filter(
        (candidate) =>
          !pens.some((pen) => trendPenKey(pen) === trendPenKey(candidate))
      ),
    [availablePens, pens]
  );

  const addPen = useCallback(
    (candidate: TrendPen) => {
      if (pens.length >= MAX_TREND_PENS) {
        return;
      }
      setPens((current) => [
        ...current,
        {
          ...candidate,
          id: trendPenKey(candidate),
          color: TREND_PEN_COLORS[current.length % TREND_PEN_COLORS.length],
        },
      ]);
      setBrushRange({});
    },
    [pens.length]
  );

  const removePen = useCallback((penId: string) => {
    setPens((current) => current.filter((pen) => pen.id !== penId));
    setBrushRange({});
  }, []);

  const resetZoom = useCallback(() => {
    setBrushRange({});
  }, []);

  const exportCsv = useCallback(() => {
    setExportError(null);
    if (merged.length === 0 || pens.length === 0) {
      setExportError(t("view.multiPenTrend.noDataExport"));
      return;
    }
    const headers = ["timestamp", ...pens.map((pen) => pen.label)];
    const rows = merged.map((point) => [
      new Date(point.t).toISOString(),
      ...pens.map((pen) => {
        const value = point[pen.id];
        return typeof value === "number" ? String(value) : "";
      }),
    ]);
    const csv = [headers, ...rows]
      .map((row) => row.map((cell) => `"${String(cell).replace(/"/g, '""')}"`).join(","))
      .join("\n");
    const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `trend-${Date.now()}.csv`;
    anchor.click();
    URL.revokeObjectURL(url);
  }, [merged, pens, t]);

  return (
    <Modal
      open
      title={t("view.multiPenTrend.title")}
      onCancel={onClose}
      footer={null}
      width={960}
      destroyOnHidden
      className="modal-multi-pen-trend"
      data-testid="multi-pen-trend-modal"
      modalRender={(node) => (
        <div onDoubleClick={resetZoom}>{node}</div>
      )}
    >
        <div className="multi-pen-trend-pens">
          {pens.map((pen, index) => (
            <Tag key={pen.id} className="multi-pen-trend-pen-chip" style={{ borderColor: pen.color }}>
              <span className="multi-pen-trend-pen-swatch" style={{ background: pen.color }} />
              <span className="multi-pen-trend-pen-label">{pen.label}</span>
              {scaleMode === "dual" && (
                <Button
                  type="text"
                  size="small"
                  className="multi-pen-trend-pen-axis"
                  aria-label={t(
                    axisFor(pen.id, index) === "left"
                      ? "view.multiPenTrend.axisLeft"
                      : "view.multiPenTrend.axisRight",
                  )}
                  onClick={() =>
                    setAxisByPenId((current) => ({
                      ...current,
                      [pen.id]: axisFor(pen.id, index) === "left" ? "right" : "left",
                    }))
                  }
                >
                  {t(
                    axisFor(pen.id, index) === "left"
                      ? "view.multiPenTrend.axisLeftShort"
                      : "view.multiPenTrend.axisRightShort",
                  )}
                </Button>
              )}
              {pens.length > 1 && (
                <Button
                  type="text"
                  size="small"
                  className="multi-pen-trend-pen-remove"
                  aria-label={t("view.multiPenTrend.removePen")}
                  onClick={() => removePen(pen.id)}
                >
                  ×
                </Button>
              )}
            </Tag>
          ))}
          {addablePens.length > 0 && pens.length < MAX_TREND_PENS && (
            <div className="multi-pen-trend-add">
              <label>
                <span className="sr-only">{t("view.multiPenTrend.addPen")}</span>
                <Select
                  value=""
                  onChange={(value) => {
                    const next = addablePens.find((pen) => pen.id === value);
                    if (next) {
                      addPen(createTrendPen(next.objectPath, next.variableName, next.label, next.valueField));
                    }
                  }}
                  options={[
                    { value: "", label: t("view.multiPenTrend.addPen") },
                    ...addablePens.map((pen) => ({ value: pen.id, label: pen.label })),
                  ]}
                />
              </label>
            </div>
          )}
          <Typography.Text type="secondary" className="multi-pen-trend-max">
            {t("view.multiPenTrend.penCount", { count: pens.length, max: MAX_TREND_PENS })}
          </Typography.Text>
          {pens.length >= MAX_TREND_PENS && (
            <Typography.Text type="warning" className="multi-pen-trend-max">
              {t("view.multiPenTrend.maxPens", { count: MAX_TREND_PENS })}
            </Typography.Text>
          )}
        </div>

        <div className="variable-history-toolbar">
          <div className="variable-history-stats">
            <Typography.Text type="secondary">
              {t("view.multiPenTrend.pointHint", { count: merged.length, limit: pointLimit })}
            </Typography.Text>
          </div>
          <div className="variable-history-controls">
            <Segmented
              size="small"
              value={scaleMode}
              onChange={(value) => setScaleMode(value as TrendScaleMode)}
              options={[
                { value: "shared", label: t("view.multiPenTrend.scaleShared") },
                { value: "normalized", label: t("view.multiPenTrend.scaleNormalized") },
                { value: "dual", label: t("view.multiPenTrend.scaleDual") },
              ]}
            />
            <Segmented
              size="small"
              value={range}
              onChange={(value) => {
                setRange(value as HistoryRange);
                setBrushRange({});
              }}
              options={rangeOptions.map((option) => ({
                value: option.id,
                label: t(`inspector:${option.labelKey}`),
              }))}
            />
            <div className="variable-history-export">
              <Button size="small" onClick={exportCsv}>
                CSV
              </Button>
            </div>
          </div>
        </div>

        <Typography.Paragraph type="secondary" className="multi-pen-trend-zoom-hint">
          {scaleMode === "normalized" ? t("view.multiPenTrend.normalizedHint") : t("view.multiPenTrend.zoomHint")}
        </Typography.Paragraph>
        {exportError && <Alert type="error" title={exportError} showIcon />}

        <div className="variable-history-chart multi-pen-trend-chart">
          {isLoading && merged.length === 0 ? (
            <Typography.Text type="secondary">{t("inspector:variables.historyPanel.loading")}</Typography.Text>
          ) : isError ? (
            <Alert type="error" showIcon title={(error as Error).message} />
          ) : visibleData.length < 2 ? (
            <Typography.Text type="secondary">{t("inspector:variables.historyPanel.notEnoughPoints")}</Typography.Text>
          ) : (
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={chartData} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                <CartesianGrid
                  strokeDasharray="3 3"
                  stroke={CHART_GRID_STROKE}
                  yAxisId={scaleMode === "dual" ? dualGridAxis : undefined}
                />
                <XAxis dataKey="time" tick={{ fontSize: 10 }} minTickGap={24} interval="preserveStartEnd" />
                {scaleMode === "dual" ? (
                  <>
                    <YAxis yAxisId="left" orientation="left" tick={{ fontSize: 10 }} width={48} domain={["auto", "auto"]} />
                    <YAxis yAxisId="right" orientation="right" tick={{ fontSize: 10 }} width={48} domain={["auto", "auto"]} />
                  </>
                ) : (
                  <YAxis
                    tick={{ fontSize: 10 }}
                    width={48}
                    domain={scaleMode === "normalized" ? [0, 100] : ["auto", "auto"]}
                  />
                )}
                <Tooltip
                  contentStyle={{ ...CHART_TOOLTIP_STYLE, fontSize: "0.8rem" }}
                  formatter={(value, name, item) => {
                    if (scaleMode !== "normalized") {
                      return [value, name];
                    }
                    const dataKey = typeof item.dataKey === "string" ? item.dataKey : undefined;
                    const pen = pens.find((entry) => normalizedPenKey(entry.id) === dataKey);
                    const raw = pen
                      ? (item.payload as Record<string, unknown> | undefined)?.[pen.id]
                      : undefined;
                    const percent = typeof value === "number" ? Math.round(value) : value;
                    return [
                      typeof raw === "number" ? `${percent}% · ${raw.toLocaleString()}` : `${percent}%`,
                      name,
                    ];
                  }}
                />
                <Legend />
                {pens.map((pen, index) => (
                  <Line
                    key={pen.id}
                    type="monotone"
                    dataKey={scaleMode === "normalized" ? normalizedPenKey(pen.id) : pen.id}
                    name={pen.label}
                    stroke={pen.color}
                    strokeWidth={2}
                    dot={false}
                    isAnimationActive={false}
                    connectNulls
                    yAxisId={scaleMode === "dual" ? axisFor(pen.id, index) : undefined}
                  />
                ))}
                <Brush
                  dataKey="time"
                  height={24}
                  stroke="var(--accent)"
                  travellerWidth={8}
                  onChange={(rangeState) => {
                    if (
                      rangeState &&
                      typeof rangeState.startIndex === "number" &&
                      typeof rangeState.endIndex === "number"
                    ) {
                      setBrushRange({
                        startIndex: rangeState.startIndex,
                        endIndex: rangeState.endIndex,
                      });
                    }
                  }}
                />
              </LineChart>
            </ResponsiveContainer>
          )}
        </div>
    </Modal>
  );
}
