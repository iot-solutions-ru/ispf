export { ANALYTICS_ROOT } from "./platformCatalogPath";

export function isAnalyticsRoot(path: string): boolean {
  return path === "root.platform.analytics";
}

export function isAnalyticsTemplatePath(path: string): boolean {
  return path.startsWith("root.platform.analytics.") && path !== "root.platform.analytics";
}

export function isAnalyticsTagDevice(
  object: { type?: string; variableNames?: string[] } | null | undefined,
): boolean {
  if (!object || object.type !== "DEVICE" || !object.variableNames) {
    return false;
  }
  return object.variableNames.includes("derivedValue") || object.variableNames.includes("oeePct");
}
