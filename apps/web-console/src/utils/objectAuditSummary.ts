export interface ObjectAuditDiff {
  before: unknown;
  after: unknown;
}

export function parseObjectAuditSummary(summaryJson: string): ObjectAuditDiff | null {
  const trimmed = summaryJson?.trim();
  if (!trimmed) {
    return null;
  }
  try {
    const parsed: unknown = JSON.parse(trimmed);
    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
      const record = parsed as Record<string, unknown>;
      if ("before" in record || "after" in record) {
        return {
          before: record.before ?? null,
          after: record.after ?? null,
        };
      }
    }
    return { before: null, after: parsed };
  } catch {
    return { before: null, after: trimmed };
  }
}

export function hasObjectAuditDiff(diff: ObjectAuditDiff | null): boolean {
  if (!diff) {
    return false;
  }
  return diff.before != null || diff.after != null;
}

export function formatAuditValue(value: unknown): string {
  if (value == null) {
    return "";
  }
  if (typeof value === "string") {
    return value;
  }
  if (typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }
  return JSON.stringify(value, null, 2);
}
