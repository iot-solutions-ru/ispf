export type TrendScaleMode = "shared" | "normalized" | "dual";
export type TrendAxis = "left" | "right";

export function defaultTrendAxis(index: number): TrendAxis {
  return index % 2 === 0 ? "left" : "right";
}

export function normalizedPenKey(penId: string): string {
  return `${penId}__pct`;
}

/** Map each pen onto 0–100 using its own min/max. A flat series sits at 50. Raw values stay on the point. */
export function withNormalizedPens<T extends Record<string, number | string>>(
  points: T[],
  penIds: string[],
): T[] {
  const ranges = new Map<string, { min: number; max: number }>();
  for (const penId of penIds) {
    let min = Number.POSITIVE_INFINITY;
    let max = Number.NEGATIVE_INFINITY;
    for (const point of points) {
      const value = point[penId];
      if (typeof value === "number" && Number.isFinite(value)) {
        min = Math.min(min, value);
        max = Math.max(max, value);
      }
    }
    if (min !== Number.POSITIVE_INFINITY) {
      ranges.set(penId, { min, max });
    }
  }
  return points.map((point) => {
    const next: Record<string, number | string> = { ...point };
    for (const penId of penIds) {
      const value = point[penId];
      const range = ranges.get(penId);
      if (typeof value !== "number" || !range) {
        continue;
      }
      const span = range.max - range.min;
      next[normalizedPenKey(penId)] = span === 0 ? 50 : ((value - range.min) / span) * 100;
    }
    return next as T;
  });
}
