export { ANALYTICS_ROOT } from "./platformCatalogPath";

export function isAnalyticsRoot(path: string): boolean {
  return path === "root.platform.analytics";
}

export function isAnalyticsTemplatePath(path: string): boolean {
  return path.startsWith("root.platform.analytics.") && path !== "root.platform.analytics";
}
