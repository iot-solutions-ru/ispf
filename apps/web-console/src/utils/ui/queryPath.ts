export { QUERIES_ROOT } from "../platform/platformCatalogPath";

export function isQueriesRoot(path: string): boolean {
  return path === "root.platform.queries";
}

export function isQueryPath(path: string): boolean {
  return path.startsWith("root.platform.queries.") && path !== "root.platform.queries";
}

/** Platform queries folder or a child query definition object. */
export function isQueryCatalogEntry(path: string, objectType?: string): boolean {
  return isQueriesRoot(path) || isQueryPath(path) || objectType === "QUERIES";
}

/** A query definition (CUSTOM under {@code root.platform.queries}), not the folder root. */
export function isQueryCatalogDefinition(path: string): boolean {
  return isQueryPath(path);
}
