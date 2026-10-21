import { isCatalogFolderPath } from "./catalogFolderPath";

/** Platform catalog folder paths that accept new child instances from Explorer. */
export const QUERIES_ROOT = "root.platform.queries";

const PLATFORM_CATALOG_SUFFIXES = [
  ".devices",
  ".mixin-blueprints",
  ".instance-types",
  ".singleton-blueprints",
  ".instances",
  ".dashboards",
  ".mimics",
  ".workflows",
  ".alert-rules",
  ".correlators",
  ".data-sources",
  ".schedules",
  ".bindings",
  ".migrations",
  ".queries",
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
    || /^root\.tenant\.[^.]+\.platform\.reports$/.test(path);
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
  return PLATFORM_CATALOG_SUFFIXES.some((suffix) => matchesCatalogSuffix(path, suffix));
}

const POSITION_SCOPED_SUFFIXES = new Set([
  ".data-sources",
  ".schedules",
  ".bindings",
  ".migrations",
  ".process-programs",
]);

function matchesCatalogSuffix(path: string, suffix: (typeof PLATFORM_CATALOG_SUFFIXES)[number]): boolean {
  if (!path.endsWith(suffix)) {
    return false;
  }
  if (POSITION_SCOPED_SUFFIXES.has(suffix)) {
    return isCatalogFolderPath(path, suffix.slice(1));
  }
  return true;
}
