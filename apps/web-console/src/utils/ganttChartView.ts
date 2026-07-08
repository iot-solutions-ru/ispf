import type { DataRecord } from "../types";
import { formatUserDateTime } from "./formatDateTime";

export interface GanttRow {
  id: number;
  label: string;
  start: number;
  end: number;
}

export interface GanttViewport {
  start: number;
  end: number;
}

export const GANTT_MIN_VIEW_SPAN = 1e-6;
export const GANTT_ZOOM_FACTOR = 1.12;

export function computeDataBounds(rows: GanttRow[]): { min: number; max: number; span: number } {
  if (rows.length === 0) {
    return { min: 0, max: 1, span: 1 };
  }
  const min = rows.reduce((m, row) => Math.min(m, row.start), Infinity);
  const max = rows.reduce((m, row) => Math.max(m, row.end), -Infinity);
  const span = max - min || 1;
  return { min, max, span };
}

export function fitGanttViewport(min: number, max: number, paddingRatio = 0.04): GanttViewport {
  const span = max - min || 1;
  const pad = span * paddingRatio;
  return { start: min - pad, end: max + pad };
}

export function ganttViewSpan(viewport: GanttViewport): number {
  return Math.max(GANTT_MIN_VIEW_SPAN, viewport.end - viewport.start);
}

export function clampGanttViewport(
  viewport: GanttViewport,
  dataMin: number,
  dataMax: number
): GanttViewport {
  const span = ganttViewSpan(viewport);
  const dataSpan = Math.max(GANTT_MIN_VIEW_SPAN, dataMax - dataMin || 1);
  const nextSpan = Math.max(span, dataSpan * 0.02);
  let start = viewport.start;
  let end = start + nextSpan;

  const dataPad = dataSpan * 0.5;
  const lo = dataMin - dataPad;
  const hi = dataMax + dataPad;
  if (end - start !== nextSpan) {
    end = start + nextSpan;
  }
  if (start < lo) {
    start = lo;
    end = lo + nextSpan;
  }
  if (end > hi) {
    end = hi;
    start = hi - nextSpan;
  }
  return { start, end };
}

export function zoomGanttViewport(
  viewport: GanttViewport,
  factor: number,
  anchor: number,
  dataMin: number,
  dataMax: number
): GanttViewport {
  const span = ganttViewSpan(viewport);
  const nextSpan = Math.max(
    GANTT_MIN_VIEW_SPAN,
    Math.min((dataMax - dataMin || 1) * 50, span / factor)
  );
  const ratio = span > 0 ? (anchor - viewport.start) / span : 0.5;
  const start = anchor - ratio * nextSpan;
  return clampGanttViewport({ start, end: start + nextSpan }, dataMin, dataMax);
}

export function panGanttViewport(
  viewport: GanttViewport,
  delta: number,
  dataMin: number,
  dataMax: number
): GanttViewport {
  return clampGanttViewport(
    { start: viewport.start + delta, end: viewport.end + delta },
    dataMin,
    dataMax
  );
}

export function timeAtTrackPixel(
  clientX: number,
  trackLeft: number,
  trackWidth: number,
  viewport: GanttViewport
): number {
  if (trackWidth <= 0) return viewport.start;
  const ratio = Math.min(1, Math.max(0, (clientX - trackLeft) / trackWidth));
  const span = ganttViewSpan(viewport);
  return viewport.start + ratio * span;
}

export function ganttBarLayout(
  row: Pick<GanttRow, "start" | "end">,
  viewport: GanttViewport
): { leftPct: number; widthPct: number; visible: boolean } {
  const span = ganttViewSpan(viewport);
  const barStart = Math.max(row.start, viewport.start);
  const barEnd = Math.min(row.end, viewport.end);
  if (barEnd <= viewport.start || barStart >= viewport.end) {
    return { leftPct: 0, widthPct: 0, visible: false };
  }
  const leftPct = ((barStart - viewport.start) / span) * 100;
  const widthPct = ((barEnd - barStart) / span) * 100;
  return { leftPct, widthPct: Math.max(widthPct, 0.35), visible: true };
}

export function formatGanttTick(value: number): string {
  if (!Number.isFinite(value)) return "";
  const abs = Math.abs(value);
  if (abs >= 1e11) {
    return formatUserDateTime(value);
  }
  if (abs >= 1e9) {
    return formatUserDateTime(value * 1000);
  }
  if (Number.isInteger(value) && abs < 1e7) {
    return String(value);
  }
  return value.toLocaleString(undefined, { maximumFractionDigits: 2 });
}

export function buildGanttTicks(viewport: GanttViewport, count = 6): number[] {
  const span = ganttViewSpan(viewport);
  if (!Number.isFinite(span) || span <= 0) return [viewport.start];
  const step = span / Math.max(1, count - 1);
  return Array.from({ length: count }, (_, index) => viewport.start + step * index);
}

export function patchGanttRowTimes(
  record: DataRecord,
  rowIndex: number,
  startField: string,
  endField: string,
  start: number,
  end: number
): DataRecord {
  const rows = record.rows.map((row, index) =>
    index === rowIndex ? { ...row, [startField]: start, [endField]: end } : row
  );
  return { schema: record.schema, rows };
}
