import type { AnalyticsCatalogEntryDto } from "../../api/analyticsCatalog";
import type { PlatformBindingEntry } from "../platform/platformBindings";
import { mapAnalyticsCatalogToBindingEntries } from "../analytics/historianExpressionBindings";

export function mapMergedExpressionCatalog(catalog: AnalyticsCatalogEntryDto[]): PlatformBindingEntry[] {
  const seen = new Set<string>();
  const merged: PlatformBindingEntry[] = [];
  for (const kind of ["reactive", "historian"] as const) {
    for (const entry of mapAnalyticsCatalogToBindingEntries(catalog, kind)) {
      if (seen.has(entry.id)) {
        continue;
      }
      seen.add(entry.id);
      merged.push(entry);
    }
  }
  return merged;
}
