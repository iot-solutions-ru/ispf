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
