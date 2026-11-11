// Extracted from widgetDemoPreview.tsx so that component modules only export components
// (react-refresh/only-export-components keeps Fast Refresh state-preserving).
import type { ChartType, DashboardWidget } from "../../types/dashboard";

type EditorDemoWidget = Pick<DashboardWidget, "type" | "demoPreviewJson"> & {
  chartType?: ChartType;
};
import type { TrendPoint } from "../../hooks/useTrendSeries";
import type { CandlestickPoint, RangeTrendPoint } from "../../hooks/useChartTrendSeries";

export function parseDemoPreview<T>(raw: string | undefined): T | null {
  if (!raw) return null;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

const TREND_DEMO = [
  { t: 1, v: 21.2 },
  { t: 2, v: 21.8 },
  { t: 3, v: 22.1 },
  { t: 4, v: 22.4 },
  { t: 5, v: 23.0 },
  { t: 6, v: 22.7 },
  { t: 7, v: 22.3 },
  { t: 8, v: 21.9 },
];

const SPARKLINE_DEMO = [20.1, 20.4, 20.8, 21.2, 21.0, 21.5, 21.3];

const PIE_DEMO = [
  { name: "Готово", value: 45 },
  { name: "В работе", value: 30 },
  { name: "Ожидание", value: 25 },
];

const GANTT_DEMO = [
  { label: "Задача A", start: 0, end: 3 },
  { label: "Задача B", start: 2, end: 6 },
  { label: "Задача C", start: 5, end: 9 },
];

const EVENT_FEED_DEMO = [
  {
    id: "demo-event-1",
    eventName: "thresholdExceeded",
    level: "WARNING",
    objectPath: "root.platform.devices.demo-sensor-01",
    timestamp: "2026-01-15T10:00:00.000Z",
    payload: { rows: [{ value: 42.5, unit: "°C", threshold: 40 }] },
  },
  {
    id: "demo-event-2",
    eventName: "thresholdExceeded",
    level: "INFO",
    objectPath: "root.platform.devices.demo-sensor-01",
    timestamp: "2026-01-15T09:58:00.000Z",
    payload: { rows: [{ value: 38.1, unit: "°C" }] },
  },
];

const WORK_QUEUE_DEMO = [
  {
    id: "demo-task-1",
    title: "Проверить датчик",
    status: "OPEN",
    instructions: "Сверить показания температуры с эталоном",
    workflowPath: "root.platform.workflows.example",
  },
  {
    id: "demo-task-2",
    title: "Подтвердить тревогу",
    status: "CLAIMED",
    instructions: "Вызвать acknowledgeAlarm на устройстве",
    workflowPath: "root.platform.workflows.example",
  },
];

const NETWORK_DEMO = {
  nodes: [
    { id: "gw", label: "Шлюз" },
    { id: "s1", label: "Датчик 1" },
    { id: "s2", label: "Датчик 2" },
    { id: "plc", label: "ПЛК" },
  ],
  edges: [
    { from: "gw", to: "s1" },
    { from: "gw", to: "s2" },
    { from: "gw", to: "plc" },
    { from: "s1", to: "s2" },
    { from: "plc", to: "s2" },
  ],
};

const BUBBLE_DEMO = [
  { x: 18, y: 35, z: 90, name: "A" },
  { x: 22, y: 55, z: 120, name: "B" },
  { x: 26, y: 72, z: 160, name: "C" },
];

const RADAR_DEMO = [
  { subject: "Temp °C", value: 22, fullMark: 50 },
  { subject: "Temp %", value: 58, fullMark: 100 },
  { subject: "Порог", value: 35, fullMark: 50 },
];

function builtinEditorDemo(widget: EditorDemoWidget): unknown | null {
  if (widget.type === "chart") {
    if (widget.chartType === "bubble") return BUBBLE_DEMO;
    if (widget.chartType === "radar") return RADAR_DEMO;
    return TREND_DEMO;
  }
  switch (widget.type) {
    case "sparkline":
      return SPARKLINE_DEMO;
    case "pie-chart":
      return PIE_DEMO;
    case "gantt-chart":
      return GANTT_DEMO;
    case "event-feed":
      return EVENT_FEED_DEMO;
    case "work-queue":
      return WORK_QUEUE_DEMO;
    case "network-graph":
      return NETWORK_DEMO;
    default:
      return null;
  }
}

/**
 * Editor-only preview. A widget's own `demoPreviewJson` wins. Palette widgets do not
 * store that field; the matching built-in payload is used instead and must be badged.
 */
export function editorDemoPreview<T>(widget: EditorDemoWidget): T | null {
  if (widget.demoPreviewJson?.trim()) {
    return parseDemoPreview<T>(widget.demoPreviewJson);
  }
  return builtinEditorDemo(widget) as T | null;
}

export function useEditorDemoRows<T>(
  widget: EditorDemoWidget,
  liveRows: T[],
  editable?: boolean
): { rows: T[]; isDemo: boolean } {
  if (editable && liveRows.length === 0) {
    const demo = editorDemoPreview<T[]>(widget);
    if (Array.isArray(demo) && demo.length > 0) {
      return { rows: demo, isDemo: true };
    }
  }
  return { rows: liveRows, isDemo: false };
}

export function buildDemoTrendPoints(
  raw: Array<{ t?: number; v: number }> | null
): TrendPoint[] {
  if (!Array.isArray(raw) || raw.length === 0) return [];
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
    const value = point.value as number;
    const wave = Math.sin(index / 2) * 2;
    const min = value - 1.5 - Math.abs(wave) * 0.3;
    const max = value + 1.5 + Math.abs(wave) * 0.3;
    return {
      t: point.t,
      time: point.time,
      min,
      max,
      avg: value,
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
    const value = point.value as number;
    const wave = Math.sin(index / 2) * 2;
    const open = previousClose ?? value - 0.4;
    const close = value + (index % 2 === 0 ? 0.3 : -0.3);
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
