export function isAnalyticsTagDevice(
  object: { type?: string; variableNames?: string[] } | null | undefined,
): boolean {
  if (!object || object.type !== "DEVICE" || !object.variableNames) {
    return false;
  }
  return object.variableNames.includes("derivedValue") || object.variableNames.includes("oeePct");
}

/** Object path from analytics tag path (`device#ruleId`). */
export function analyticsTagObjectPath(tagPath: string): string {
  const hash = tagPath.indexOf("#");
  return hash >= 0 ? tagPath.slice(0, hash) : tagPath;
}
