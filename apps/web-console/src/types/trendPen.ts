export interface TrendPen {
  id: string;
  objectPath: string;
  variableName: string;
  valueField?: string;
  label: string;
  color: string;
}

export const TREND_PEN_COLORS = ["#2f81f7", "#e5534b", "#3fb950", "#d29922"] as const;

export const MAX_TREND_PENS = 4;

export function trendPenKey(pen: Pick<TrendPen, "objectPath" | "variableName" | "valueField">): string {
  return `${pen.objectPath}|${pen.variableName}|${pen.valueField ?? "value"}`;
}

export function createTrendPen(
  objectPath: string,
  variableName: string,
  label: string,
  valueField?: string,
  colorIndex = 0
): TrendPen {
  return {
    id: trendPenKey({ objectPath, variableName, valueField }),
    objectPath,
    variableName,
    valueField,
    label,
    color: TREND_PEN_COLORS[colorIndex % TREND_PEN_COLORS.length],
  };
}
