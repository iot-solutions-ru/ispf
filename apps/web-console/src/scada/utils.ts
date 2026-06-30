export function asBool(raw: unknown): boolean {
  return raw === true || raw === "true" || raw === 1 || raw === "1";
}

export function asNum(raw: unknown): number | null {
  if (typeof raw === "number" && Number.isFinite(raw)) return raw;
  if (typeof raw === "string" && raw.trim() !== "") {
    const n = Number(raw);
    return Number.isFinite(n) ? n : null;
  }
  return null;
}

export function fmtNum(raw: unknown, decimals = 0, suffix = "", pattern?: string): string {
  const n = asNum(raw);
  if (n == null) return "—";
  if (pattern && /^0+$/.test(pattern)) {
    const width = pattern.length;
    const rounded = decimals > 0 ? n.toFixed(decimals) : String(Math.round(n));
    const padded = rounded.padStart(width, "0");
    return `${padded}${suffix}`;
  }
  return `${decimals > 0 ? n.toFixed(decimals) : Math.round(n)}${suffix}`;
}

/** RD: unreliable analog values display as gray asterisks. */
export function fmtNumWithQuality(
  raw: unknown,
  quality: unknown,
  decimals = 0,
  suffix = "",
  pattern?: string
): string {
  if (quality === "bad" || quality === "unreliable" || quality === false) {
    return pattern ? "*".repeat(pattern.length) : "*****";
  }
  if (quality === "uncertain") {
    return fmtNum(raw, decimals, suffix, pattern);
  }
  return fmtNum(raw, decimals, suffix, pattern);
}

export function isBadQuality(quality: unknown): boolean {
  return quality === "bad" || quality === "unreliable" || quality === false;
}

export function fmtText(raw: unknown, fallback = "—"): string {
  if (raw == null || raw === "") return fallback;
  return String(raw);
}

export function clamp01(value: number): number {
  return Math.max(0, Math.min(1, value));
}

/** Static demo fallback when live binding is absent (editor / showcase templates). */
export function demoVal(
  values: Record<string, unknown>,
  props: Record<string, unknown>,
  key: string
): unknown {
  const v = values[key];
  if (v !== undefined && v !== null && v !== "") return v;
  return props[key];
}
