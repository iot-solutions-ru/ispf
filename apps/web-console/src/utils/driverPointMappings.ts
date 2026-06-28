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
        result[key] = value === null || value === undefined ? "" : String(value);
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
