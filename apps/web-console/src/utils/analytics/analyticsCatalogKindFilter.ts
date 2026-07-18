import type { AnalyticsCatalogEntryDto } from "../../api/analyticsCatalog";

export type AnalyticsFormulaKindFilter = "historian" | "reactive" | "all";

export function matchesAnalyticsCatalogKindFilter(
  entry: AnalyticsCatalogEntryDto,
  kindFilter: AnalyticsFormulaKindFilter
): boolean {
  if (kindFilter === "all") {
    return true;
  }
  const normalized = kindFilter.toLowerCase();
  if (entry.kinds.some((kind) => kind.toLowerCase() === normalized)) {
    return true;
  }
  return entry.tags.some((tag) => tag.toLowerCase() === normalized);
}
