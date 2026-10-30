import type { VariableDto } from "../../types";
import { readFieldValue } from "../../types/dashboard";
import type { TrendPoint } from "../../hooks/useTrendSeries";

export interface ChartBubblePoint {
  name: string;
  x: number;
  y: number;
  z: number;
  time?: string;
}

export interface ChartBubblePointConfig {
  label: string;
  xVariable: string;
  yVariable: string;
  sizeVariable?: string;
  xValueField?: string;
  yValueField?: string;
  sizeValueField?: string;
}

export interface ChartRadarAxis {
  label: string;
  variableName: string;
  valueField?: string;
  /** Scale maximum for Recharts `fullMark` (default 100). */
  max?: number;
}

export interface ChartRadarRow {
  subject: string;
  value: number;
  fullMark: number;
}

function toNumeric(raw: unknown): number | null {
  if (typeof raw === "number" && Number.isFinite(raw)) {
    return raw;
  }
  if (typeof raw === "boolean") {
    return raw ? 1 : 0;
  }
  if (typeof raw === "string" && raw.trim() !== "") {
    const parsed = Number(raw);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

function readVariableNumeric(
  variables: VariableDto[] | undefined,
  variableName: string,
  valueField = "value"
): number | null {
  if (!variableName || !variables) {
    return null;
  }
  const variable = variables.find((item) => item.name === variableName);
  const row = variable?.value?.rows[0];
  return toNumeric(readFieldValue(row, valueField));
}

export function parseBubblePointsJson(raw: string | undefined): ChartBubblePointConfig[] {
  if (!raw?.trim()) {
    return [];
  }
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed
      .map((item): ChartBubblePointConfig | null => {
        if (!item || typeof item !== "object") {
          return null;
        }
        const row = item as Record<string, unknown>;
        const label = String(row.label ?? row.name ?? "").trim();
        const xVariable = String(row.xVariable ?? "").trim();
        const yVariable = String(row.yVariable ?? "").trim();
        if (!label || !xVariable || !yVariable) {
          return null;
        }
        return {
          label,
          xVariable,
          yVariable,
          sizeVariable: row.sizeVariable ? String(row.sizeVariable).trim() : undefined,
          xValueField: row.xValueField ? String(row.xValueField) : undefined,
          yValueField: row.yValueField ? String(row.yValueField) : undefined,
          sizeValueField: row.sizeValueField ? String(row.sizeValueField) : undefined,
        };
      })
      .filter((item): item is ChartBubblePointConfig => item != null);
  } catch {
    return [];
  }
}

export function parseRadarAxesJson(raw: string | undefined): ChartRadarAxis[] {
  if (!raw?.trim()) {
    return [];
  }
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed
      .map((item): ChartRadarAxis | null => {
        if (!item || typeof item !== "object") {
          return null;
        }
        const row = item as Record<string, unknown>;
        const label = String(row.label ?? row.subject ?? "").trim();
        const variableName = String(row.variableName ?? row.variable ?? "").trim();
        if (!label || !variableName) {
          return null;
        }
        const maxRaw = row.max ?? row.fullMark;
        const max =
          maxRaw != null && Number.isFinite(Number(maxRaw)) && Number(maxRaw) > 0
            ? Number(maxRaw)
            : undefined;
        return {
          label,
          variableName,
          valueField: row.valueField ? String(row.valueField) : undefined,
          max,
        };
      })
      .filter((item): item is ChartRadarAxis => item != null);
  } catch {
    return [];
  }
}

function bubblePointRecord(point: ChartBubblePointConfig): Record<string, string> | null {
  const label = point.label.trim();
  const xVariable = point.xVariable.trim();
  const yVariable = point.yVariable.trim();
  if (!label || !xVariable || !yVariable) {
    return null;
  }
  const row: Record<string, string> = { label, xVariable, yVariable };
  const sizeVariable = point.sizeVariable?.trim();
  if (sizeVariable) {
    row.sizeVariable = sizeVariable;
  }
  if (point.xValueField) {
    row.xValueField = point.xValueField;
  }
  if (point.yValueField) {
    row.yValueField = point.yValueField;
  }
  if (point.sizeValueField) {
    row.sizeValueField = point.sizeValueField;
  }
  return row;
}

/** Stores complete bubble rows. Incomplete drafts stay out of the saved JSON. */
export function serializeBubblePoints(points: ChartBubblePointConfig[]): string | undefined {
  const rows = points
    .map(bubblePointRecord)
    .filter((row): row is Record<string, string> => row != null);
  return rows.length > 0 ? JSON.stringify(rows) : undefined;
}

function radarAxisRecord(axis: ChartRadarAxis): Record<string, string | number> | null {
  const label = axis.label.trim();
  const variableName = axis.variableName.trim();
  if (!label || !variableName) {
    return null;
  }
  const row: Record<string, string | number> = { label, variableName };
  if (axis.valueField) {
    row.valueField = axis.valueField;
  }
  if (axis.max != null && Number.isFinite(axis.max) && axis.max > 0) {
    row.max = axis.max;
  }
  return row;
}

/** Stores complete radar axes. Incomplete drafts stay out of the saved JSON. */
export function serializeRadarAxes(axes: ChartRadarAxis[]): string | undefined {
  const rows = axes
    .map(radarAxisRecord)
    .filter((row): row is Record<string, string | number> => row != null);
  return rows.length > 0 ? JSON.stringify(rows) : undefined;
}

export function buildBubbleSnapshotPoints(
  configs: ChartBubblePointConfig[],
  variables: VariableDto[] | undefined,
  defaultSize: number
): ChartBubblePoint[] {
  return configs
    .map((config) => {
      const x = readVariableNumeric(variables, config.xVariable, config.xValueField ?? "value");
      const y = readVariableNumeric(variables, config.yVariable, config.yValueField ?? "value");
      if (x == null || y == null) {
        return null;
      }
      const sizeRaw = config.sizeVariable
        ? readVariableNumeric(variables, config.sizeVariable, config.sizeValueField ?? "value")
        : null;
      const z = sizeRaw != null && sizeRaw > 0 ? sizeRaw : defaultSize;
      return {
        name: config.label,
        x,
        y,
        z,
      } satisfies ChartBubblePoint;
    })
    .filter((item): item is ChartBubblePoint => item != null);
}

export function zipBubbleTrajectoryPoints(
  xSeries: TrendPoint[],
  ySeries: TrendPoint[],
  sizeValue: unknown,
  defaultSize: number
): ChartBubblePoint[] {
  if (xSeries.length === 0 || ySeries.length === 0) {
    return [];
  }
  const sizeNumeric = toNumeric(sizeValue);
  const z = sizeNumeric != null && sizeNumeric > 0 ? sizeNumeric : defaultSize;
  const count = Math.min(xSeries.length, ySeries.length);
  const points: ChartBubblePoint[] = [];
  for (let index = 0; index < count; index += 1) {
    const xPoint = xSeries[index];
    const yPoint = ySeries[index];
    if (xPoint.value == null || yPoint.value == null) {
      continue;
    }
    points.push({
      name: xPoint.time,
      x: xPoint.value,
      y: yPoint.value,
      z,
      time: xPoint.time,
    });
  }
  return points;
}

export function buildRadarRows(
  axes: ChartRadarAxis[],
  variables: VariableDto[] | undefined
): ChartRadarRow[] {
  return axes
    .map((axis) => {
      const value = readVariableNumeric(variables, axis.variableName, axis.valueField ?? "value");
      if (value == null) {
        return null;
      }
      return {
        subject: axis.label,
        value,
        fullMark: axis.max ?? 100,
      } satisfies ChartRadarRow;
    })
    .filter((item): item is ChartRadarRow => item != null);
}

export function parseDemoBubblePoints(raw: unknown): ChartBubblePoint[] {
  if (!Array.isArray(raw)) {
    return [];
  }
  return raw
    .map((item, index): ChartBubblePoint | null => {
      if (!item || typeof item !== "object") {
        return null;
      }
      const row = item as Record<string, unknown>;
      const x = toNumeric(row.x);
      const y = toNumeric(row.y);
      if (x == null || y == null) {
        return null;
      }
      const zRaw = toNumeric(row.z);
      const z = zRaw != null && zRaw > 0 ? zRaw : 80;
      const name = String(row.name ?? row.label ?? `P${index + 1}`);
      const point: ChartBubblePoint = { name, x, y, z };
      if (row.time) {
        point.time = String(row.time);
      }
      return point;
    })
    .filter((item): item is ChartBubblePoint => item != null);
}

/**
 * Bubble axis limits. An empty end follows the points. Both ends fix the axis
 * when the minimum is below the maximum; otherwise the axis stays automatic.
 */
/** Editor limits for bubble area. Recharts draws `z` as symbol area, not radius. */
const BUBBLE_AREA_MIN = 10;
const BUBBLE_AREA_MAX = 1000;

/**
 * Pixel area for the bubble Z axis.
 * A single shared size (the default, or one size variable on a trajectory) has a
 * zero-width domain. Recharts then paints the middle of the range, so a fixed
 * range hides the size field. The range is the requested area in that case.
 */
export function bubbleSymbolScale(points: Array<{ z: number }>): {
  domain: [number, number];
  range: [number, number];
} {
  const values = points.map((point) => point.z).filter((z) => Number.isFinite(z) && z > 0);
  if (values.length === 0) {
    return { domain: [80, 80], range: [80, 80] };
  }
  const min = Math.min(...values);
  const max = Math.max(...values);
  const clamp = (n: number) => Math.min(BUBBLE_AREA_MAX, Math.max(BUBBLE_AREA_MIN, n));
  return { domain: [min, max], range: [clamp(min), clamp(max)] };
}

export function bubbleAxisDomain(
  min: number | undefined,
  max: number | undefined
): [number | "auto", number | "auto"] | undefined {
  const hasMin = typeof min === "number" && Number.isFinite(min);
  const hasMax = typeof max === "number" && Number.isFinite(max);
  if (hasMin && hasMax) {
    return min < max ? [min, max] : undefined;
  }
  if (hasMin) {
    return [min, "auto"];
  }
  if (hasMax) {
    return ["auto", max];
  }
  return undefined;
}

export function parseDemoRadarRows(raw: unknown): ChartRadarRow[] {
  if (!Array.isArray(raw)) {
    return [];
  }
  return raw
    .map((item) => {
      if (!item || typeof item !== "object") {
        return null;
      }
      const row = item as Record<string, unknown>;
      const subject = String(row.subject ?? row.label ?? "").trim();
      const value = toNumeric(row.value);
      if (!subject || value == null) {
        return null;
      }
      const fullMarkRaw = toNumeric(row.fullMark ?? row.max);
      const fullMark = fullMarkRaw != null && fullMarkRaw > 0 ? fullMarkRaw : 100;
      return { subject, value, fullMark };
    })
    .filter((item): item is ChartRadarRow => item != null);
}
