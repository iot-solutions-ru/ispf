import type { DashboardWidget } from "../../types/dashboard";

/** Widget fields that name a dashboard session.selection slot. */
const SELECTION_KEY_FIELDS = [
  "selectionKey",
  "rowSelectionKey",
  "cardSelectionKey",
  "rowTargetSelectionKey",
] as const;

/** Unique selection-slot names already set on widgets of this dashboard, sorted. */
export function collectDashboardSelectionKeys(widgets: DashboardWidget[]): string[] {
  const keys = new Set<string>();
  for (const widget of widgets) {
    const record = widget as unknown as Record<string, unknown>;
    for (const field of SELECTION_KEY_FIELDS) {
      const value = record[field];
      if (typeof value === "string" && value.trim()) {
        keys.add(value.trim());
      }
    }
  }
  return [...keys].sort((a, b) => a.localeCompare(b));
}
