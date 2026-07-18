/** Normalized telemetry quality (ADR-0025, BL-82). */

export type TelemetryQualityLevel = "GOOD" | "UNCERTAIN" | "BAD";

export function parseTelemetryQuality(raw: unknown): TelemetryQualityLevel {
  if (typeof raw !== "string" || raw.trim() === "") {
    return "GOOD";
  }
  const normalized = raw.trim().toUpperCase();
  if (normalized === "BAD") {
    return "BAD";
  }
  if (normalized === "UNCERTAIN" || normalized === "UNCERT") {
    return "UNCERTAIN";
  }
  return "GOOD";
}

/** Whether chart/trend widgets should plot this sample (BAD → gap). */
export function isPlottableTelemetryQuality(raw: unknown): boolean {
  return parseTelemetryQuality(raw) !== "BAD";
}

export function readRowQuality(row: Record<string, unknown> | undefined): TelemetryQualityLevel | undefined {
  if (!row || !("quality" in row)) {
    return undefined;
  }
  return parseTelemetryQuality(row.quality);
}
