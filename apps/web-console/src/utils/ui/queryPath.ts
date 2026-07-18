export { QUERIES_ROOT } from "../platform/platformCatalogPath";

export function isQueriesRoot(path: string): boolean {
  return path === "root.platform.queries";
}

export function isQueryPath(path: string): boolean {
  return path.startsWith("root.platform.queries.") && path !== "root.platform.queries";
}
