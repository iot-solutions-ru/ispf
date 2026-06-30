function extractPointAddress(value: unknown): string {
  if (value === null || value === undefined) {
    return "";
  }
  if (typeof value === "string") {
    return value;
  }
  if (typeof value === "object" && !Array.isArray(value)) {
    const record = value as Record<string, unknown>;
    for (const key of ["point", "address", "pointId"]) {
      const candidate = record[key];
      if (typeof candidate === "string" && candidate.trim()) {
        return candidate;
      }
    }
  }
  return String(value);
}

export function parseDriverPointMappings(raw: string): Record<string, string> {
  if (!raw.trim()) {
    return {};
  }
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      return {};
    }
    const result: Record<string, string> = {};
    for (const [key, value] of Object.entries(parsed as Record<string, unknown>)) {
      if (typeof key === "string" && key.trim()) {
        result[key] = extractPointAddress(value);
      }
    }
    return result;
  } catch {
    return {};
  }
}

export function parseDriverWriteValue(raw: string): Record<string, unknown> {
  const trimmed = raw.trim();
  if (trimmed === "true") {
    return { value: true };
  }
  if (trimmed === "false") {
    return { value: false };
  }
  const num = Number(trimmed);
  if (trimmed !== "" && Number.isFinite(num)) {
    return { value: num };
  }
  return { value: trimmed };
}
