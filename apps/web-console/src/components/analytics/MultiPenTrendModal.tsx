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
  const [pens, setPens] = useState<TrendPen[]>(initialPens);
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
          {pens.map((pen) => (
            <Tag key={pen.id} className="multi-pen-trend-pen-chip" style={{ borderColor: pen.color }}>
              <span className="multi-pen-trend-pen-swatch" style={{ background: pen.color }} />
              <span className="multi-pen-trend-pen-label">{pen.label}</span>
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
          {pens.length >= MAX_TREND_PENS && (
            <Typography.Text type="secondary" className="multi-pen-trend-max">
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
          {t("view.multiPenTrend.zoomHint")}
        </Typography.Paragraph>
        {exportError && <Alert type="error" message={exportError} showIcon />}

        <div className="variable-history-chart multi-pen-trend-chart">
          {isLoading && merged.length === 0 ? (
            <Typography.Text type="secondary">{t("inspector:variables.historyPanel.loading")}</Typography.Text>
          ) : isError ? (
            <Alert type="error" showIcon message={(error as Error).message} />
          ) : visibleData.length < 2 ? (
            <Typography.Text type="secondary">{t("inspector:variables.historyPanel.notEnoughPoints")}</Typography.Text>
          ) : (
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={visibleData} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke={CHART_GRID_STROKE} />
                <XAxis dataKey="time" tick={{ fontSize: 10 }} minTickGap={24} interval="preserveStartEnd" />
                <YAxis tick={{ fontSize: 10 }} width={48} domain={["auto", "auto"]} />
                <Tooltip contentStyle={{ ...CHART_TOOLTIP_STYLE, fontSize: "0.8rem" }} />
                <Legend />
                {pens.map((pen) => (
                  <Line
                    key={pen.id}
                    type="monotone"
                    dataKey={pen.id}
                    name={pen.label}
                    stroke={pen.color}
                    strokeWidth={2}
                    dot={false}
                    isAnimationActive={false}
                    connectNulls
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
