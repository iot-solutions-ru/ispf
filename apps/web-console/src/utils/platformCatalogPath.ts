/** Platform catalog folder paths that accept new child instances from Explorer. */
export const QUERIES_ROOT = "root.platform.queries";
export const ANALYTICS_ROOT = "root.platform.analytics";

const PLATFORM_CATALOG_SUFFIXES = [
  ".devices",
  ".relative-blueprints",
  ".instance-types",
  ".absolute-blueprints",
  ".instances",
  ".dashboards",
  ".mimics",
  ".reports",
  ".workflows",
  ".alert-rules",
  ".correlators",
  ".data-sources",
  ".schedules",
  ".bindings",
  ".migrations",
  ".queries",
  ".analytics",
  ".event-filters",
  ".process-programs",
  ".mes",
  ".work-orders",
  ".operations",
  ".lots",
  ".shifts",
  ".quality-records",
] as const;

export function isPlatformReportsFolder(path: string): boolean {
  return path === "root.platform.reports"
    || (path.endsWith(".reports") && !path.startsWith("root.platform.applications."));
}

export function isMesCatalogContainer(path: string): boolean {
  return (
    path.endsWith(".mes")
    || path.endsWith(".work-orders")
    || path.endsWith(".operations")
    || path.endsWith(".lots")
    || path.endsWith(".shifts")
    || path.endsWith(".quality-records")
    || (path.endsWith(".instances") && path.includes(".mes."))
  );
}

export function isPlatformCatalogContainer(path: string): boolean {
  if (path === "root" || path === "root.platform") {
    return true;
  }
  if (isPlatformReportsFolder(path) || isMesCatalogContainer(path)) {
    return true;
  }
  return PLATFORM_CATALOG_SUFFIXES.some((suffix) => path.endsWith(suffix));
}
