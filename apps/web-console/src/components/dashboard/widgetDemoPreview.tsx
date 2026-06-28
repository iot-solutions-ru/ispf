import type { DashboardWidget } from "../../types/dashboard";
import type { TrendPoint } from "../../hooks/useTrendSeries";
import type { CandlestickPoint, RangeTrendPoint } from "../../hooks/useChartTrendSeries";
import { useTranslation } from "react-i18next";

export function parseDemoPreview<T>(raw: string | undefined): T | null {
  if (!raw) return null;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

export function useEditorDemoRows<T>(
  widget: Pick<DashboardWidget, "demoPreviewJson">,
  liveRows: T[],
  editable?: boolean
): { rows: T[]; isDemo: boolean } {
  const demo = parseDemoPreview<T[]>(widget.demoPreviewJson);
  if (editable && liveRows.length === 0 && Array.isArray(demo) && demo.length > 0) {
    return { rows: demo, isDemo: true };
  }
  return { rows: liveRows, isDemo: false };
}

export function buildDemoTrendPoints(
  raw: Array<{ t?: number; v: number }> | null
): TrendPoint[] {
  if (!raw?.length) return [];
  const now = Date.now();
  return raw.map((item, index) => {
    const t = item.t ?? now - (raw.length - index) * 60_000;
    return {
      t,
      time: new Date(t).toLocaleTimeString(),
      value: item.v,
    };
  });
}

export function buildDemoRangeTrendPoints(
  raw: Array<{ t?: number; v: number }> | null
): RangeTrendPoint[] {
  const points = buildDemoTrendPoints(raw);
  if (points.length === 0) {
    return [];
  }
  return points.map((point, index) => {
    const wave = Math.sin(index / 2) * 2;
    const min = point.value - 1.5 - Math.abs(wave) * 0.3;
    const max = point.value + 1.5 + Math.abs(wave) * 0.3;
    return {
      ...point,
      min,
      max,
      avg: point.value,
      band: Math.max(0, max - min),
    };
  });
}

export function buildDemoCandlestickPoints(
  raw: Array<{ t?: number; v: number }> | null
): CandlestickPoint[] {
  const points = buildDemoTrendPoints(raw);
  if (points.length === 0) {
    return [];
  }
  let previousClose: number | null = null;
  return points.map((point, index) => {
    const wave = Math.sin(index / 2) * 2;
    const open = previousClose ?? point.value - 0.4;
    const close = point.value + (index % 2 === 0 ? 0.3 : -0.3);
    const high = Math.max(open, close) + 1.2 + Math.abs(wave) * 0.2;
    const low = Math.min(open, close) - 1.2 - Math.abs(wave) * 0.2;
    previousClose = close;
    return {
      t: point.t,
      time: point.time,
      open,
      high,
      low,
      close,
    };
  });
}

export function WidgetDemoBadge() {
  const { t } = useTranslation("widgets");
  return <span className="dash-widget-demo-badge">{t("view.demoBadge")}</span>;
}
