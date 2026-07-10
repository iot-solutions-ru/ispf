import type { AnalyticsCatalogEntryDto } from "../api/analyticsCatalog";

export type AnalyticsFormulaKindFilter = "historian" | "reactive";

export function matchesAnalyticsCatalogKindFilter(
  entry: AnalyticsCatalogEntryDto,
  kindFilter: AnalyticsFormulaKindFilter
): boolean {
  const normalized = kindFilter.toLowerCase();
  if (entry.kinds.some((kind) => kind.toLowerCase() === normalized)) {
    return true;
  }
  return entry.tags.some((tag) => tag.toLowerCase() === normalized);
}
