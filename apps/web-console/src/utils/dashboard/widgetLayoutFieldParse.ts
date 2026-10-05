/** Max x so the widget still fits (same rule as dashboard grid drag). */
export function layoutGridMaxX(columns: number, widgetW: number): number {
  return Math.max(0, columns - Math.max(1, Math.trunc(widgetW)));
}

/** Max w for current x. */
export function layoutGridMaxW(columns: number, widgetX: number): number {
  return Math.max(1, columns - Math.max(0, Math.trunc(widgetX)));
}

/** Parse grid layout integer from editor input; ignore empty/invalid (do not coerce to 0). */
export function parseLayoutGridInt(raw: string, min: number, max?: number): number | undefined {
  const trimmed = raw.trim();
  if (trimmed === "") {
    return undefined;
  }
  const n = Number(trimmed);
  if (!Number.isFinite(n)) {
    return undefined;
  }
  let int = Math.trunc(n);
  if (int < min) {
    int = min;
  }
  if (max !== undefined && int > max) {
    int = max;
  }
  return int;
}

/** Optional z-index: empty clears; invalid input is ignored. */
export function parseOptionalZIndex(raw: string): number | undefined | null {
  const trimmed = raw.trim();
  if (trimmed === "") {
    return undefined;
  }
  const n = Number(trimmed);
  if (!Number.isFinite(n)) {
    return null;
  }
  return Math.trunc(n);
}
