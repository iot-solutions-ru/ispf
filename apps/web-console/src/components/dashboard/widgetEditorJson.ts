// Extracted from widgetEditorStructured.tsx so that component modules only export components
// (react-refresh/only-export-components keeps Fast Refresh state-preserving).

export function parseJsonArray<T>(raw: string | undefined, fallback: T[] = []): T[] {
  if (!raw?.trim()) return fallback;
  try {
    const parsed = JSON.parse(raw) as unknown;
    return Array.isArray(parsed) ? (parsed as T[]) : fallback;
  } catch {
    return fallback;
  }
}

export function parseJsonObject(raw: string | undefined): Record<string, string> {
  if (!raw?.trim()) return {};
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return {};
    const result: Record<string, string> = {};
    for (const [k, v] of Object.entries(parsed as Record<string, unknown>)) {
      if (v !== undefined && v !== null) result[k] = String(v);
    }
    return result;
  } catch {
    return {};
  }
}

export function stringifyJson(value: unknown): string {
  return JSON.stringify(value, null, 2);
}
